<?php
declare(strict_types=1);

require dirname(__DIR__) . '/includes/auth-common.php';

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, max-age=0');
header('Pragma: no-cache');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');
header('X-Frame-Options: DENY');

const RASPORED_AI_MAX_IMAGE_BYTES = 10485760;
const RASPORED_AI_RATE_WINDOW = 3600;
const RASPORED_AI_RATE_LIMIT = 20;

function ai_fail(int $status, string $message): never
{
    http_response_code($status);
    echo json_encode(
        ['ok' => false, 'error' => $message],
        JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES
    );
    exit;
}

function ai_text_slice(string $value, int $max): string
{
    return function_exists('mb_substr')
        ? mb_substr($value, 0, $max, 'UTF-8')
        : substr($value, 0, $max);
}

function ai_normalize_code(mixed $raw): string
{
    if (!is_string($raw)) {
        return '';
    }
    $value = trim($raw);
    $value = function_exists('mb_strtoupper')
        ? mb_strtoupper($value, 'UTF-8')
        : strtoupper($value);
    if ($value === 'G0') {
        $value = 'GO';
    } elseif ($value === 'B0') {
        $value = 'BO';
    }
    return preg_match('/^[\p{L}\p{N}]{1,8}$/u', $value) === 1 ? $value : '';
}

function ai_rate_limit(string $accountId): void
{
    $dir = raspored_storage_directory();
    $path = $dir . '/ai-rate-' . hash_hmac('sha256', $accountId, raspored_install_secret()) . '.json';
    $now = time();
    $lock = @fopen($path . '.lock', 'c');
    if ($lock === false || !flock($lock, LOCK_EX)) {
        if (is_resource($lock)) {
            fclose($lock);
        }
        ai_fail(503, 'AI provjera trenutačno nije dostupna.');
    }

    try {
        $events = [];
        if (is_file($path)) {
            $decoded = json_decode((string) @file_get_contents($path), true);
            if (is_array($decoded)) {
                $events = array_values(array_filter(
                    array_map('intval', $decoded),
                    static fn (int $timestamp): bool => $timestamp > $now - RASPORED_AI_RATE_WINDOW
                ));
            }
        }
        if (count($events) >= RASPORED_AI_RATE_LIMIT) {
            ai_fail(429, 'Dosegnut je privremeni limit AI provjera. Pokušaj ponovno kasnije.');
        }
        $events[] = $now;
        $json = json_encode($events);
        if ($json !== false) {
            @file_put_contents($path, $json, LOCK_EX);
            @chmod($path, 0600);
        }
    } finally {
        flock($lock, LOCK_UN);
        fclose($lock);
    }
}

function ai_extract_output_text(array $payload): string
{
    if (is_string($payload['output_text'] ?? null)) {
        return (string) $payload['output_text'];
    }
    foreach (($payload['output'] ?? []) as $item) {
        if (!is_array($item)) {
            continue;
        }
        foreach (($item['content'] ?? []) as $part) {
            if (is_array($part)
                && ($part['type'] ?? '') === 'output_text'
                && is_string($part['text'] ?? null)
            ) {
                return (string) $part['text'];
            }
        }
    }
    return '';
}

function ai_clean_result(array $raw): array
{
    $month = null;
    if (is_array($raw['month'] ?? null)) {
        $year = (int) ($raw['month']['year'] ?? 0);
        $number = (int) ($raw['month']['month'] ?? 0);
        if ($year >= 2000 && $year <= 2100 && $number >= 1 && $number <= 12) {
            $month = ['year' => $year, 'month' => $number];
        }
    }

    $people = [];
    foreach (array_slice(is_array($raw['people'] ?? null) ? $raw['people'] : [], 0, 100) as $person) {
        if (!is_array($person)) {
            continue;
        }
        $name = trim((string) ($person['name'] ?? ''));
        $name = preg_replace('/\s+/u', ' ', $name) ?? '';
        $name = ai_text_slice($name, 100);
        if ((function_exists('mb_strlen') ? mb_strlen($name, 'UTF-8') : strlen($name)) < 2) {
            continue;
        }

        $row = is_numeric($person['row'] ?? null) ? (int) $person['row'] : null;
        if ($row !== null && ($row < 1 || $row > 100)) {
            $row = null;
        }

        $dayShifts = [];
        foreach (array_slice(is_array($person['shifts'] ?? null) ? $person['shifts'] : [], 0, 31) as $shift) {
            if (!is_array($shift)) {
                continue;
            }
            $day = (int) ($shift['day'] ?? 0);
            $code = ai_normalize_code($shift['code'] ?? '');
            if ($day >= 1 && $day <= 31 && $code !== '') {
                $dayShifts[(string) $day] = $code;
            }
        }
        ksort($dayShifts, SORT_NUMERIC);
        $people[] = ['row' => $row, 'name' => $name, 'dayShifts' => $dayShifts];
    }

    usort($people, static function (array $a, array $b): int {
        $left = is_int($a['row']) ? $a['row'] : PHP_INT_MAX;
        $right = is_int($b['row']) ? $b['row'] : PHP_INT_MAX;
        return $left <=> $right;
    });

    return [
        'month' => $month,
        'people' => $people,
        'expectedRows' => max(0, min(100, (int) ($raw['expectedRows'] ?? 0))),
        'notes' => ai_text_slice(trim((string) ($raw['notes'] ?? '')), 500),
    ];
}

$method = strtoupper((string) ($_SERVER['REQUEST_METHOD'] ?? ''));
if ($method !== 'POST') {
    header('Allow: POST');
    ai_fail(405, 'Metoda nije dopuštena.');
}
if ((string) ($_SERVER['HTTP_X_RASPORED_REQUEST'] ?? '') !== '1') {
    ai_fail(403, 'Zahtjev nije dopušten.');
}

$mobile = raspored_mobile_client_request();
if ($mobile && !raspored_secure_api_transport_ok()) {
    ai_fail(403, 'Android AI provjera zahtijeva HTTPS.');
}
if (!$mobile && !raspored_same_origin_ok()) {
    ai_fail(403, 'Izvor zahtjeva nije dopušten.');
}

$account = $mobile
    ? raspored_mobile_account_from_token(raspored_bearer_token())
    : raspored_current_account();
if ($account === null) {
    ai_fail(401, 'AI provjera dostupna je prijavljenim korisnicima.');
}

$apiKey = trim((string) getenv('OPENAI_API_KEY'));
if ($apiKey === '') {
    ai_fail(503, 'AI provjera nije konfigurirana na ovom poslužitelju.');
}
$model = trim((string) getenv('OPENAI_RASPORED_MODEL'));
if ($model === '') {
    $model = 'gpt-5.6-terra';
}
if (!preg_match('/^[a-zA-Z0-9._-]{1,80}$/', $model)) {
    ai_fail(500, 'AI model nije ispravno konfiguriran.');
}

ai_rate_limit((string) ($account['id'] ?? ''));

$file = $_FILES['image'] ?? null;
if (!is_array($file)
    || (int) ($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK
    || !is_uploaded_file((string) ($file['tmp_name'] ?? ''))
) {
    ai_fail(400, 'Slika rasporeda nije zaprimljena.');
}

$size = (int) ($file['size'] ?? 0);
if ($size < 1 || $size > RASPORED_AI_MAX_IMAGE_BYTES) {
    ai_fail(413, 'Slika je prevelika. Najveća dopuštena veličina je 10 MB.');
}

$finfo = new finfo(FILEINFO_MIME_TYPE);
$mime = (string) $finfo->file((string) $file['tmp_name']);
if (!in_array($mime, ['image/jpeg', 'image/png', 'image/webp'], true)) {
    ai_fail(415, 'Podržane su JPEG, PNG i WebP slike.');
}
$imageBytes = @file_get_contents((string) $file['tmp_name']);
if (!is_string($imageBytes) || $imageBytes === '') {
    ai_fail(400, 'Sliku nije moguće pročitati.');
}

$monthHint = trim((string) ($_POST['monthHint'] ?? ''));
$localOcr = trim((string) ($_POST['localOcr'] ?? ''));
$localOcr = ai_text_slice($localOcr, 12000);

$prompt = <<<'PROMPT'
Analiziraj fotografiju mjesečnog rasporeda rada. Izvuci SVAKI numerirani redak djelatnika i SVAKU vidljivu oznaku po točnom stupcu dana. Ne sažimaj prazne dane i ne pomiči oznake ulijevo. Brojevi 1–31 u zaglavlju određuju stupce. Vrati redni broj, ime i prezime kako su čitljivi, te samo stvarno vidljive oznake za dane.

Standardne oznake: D=dnevna smjena, N=noćna smjena, GO=godišnji odmor, BO=bolovanje, PD=plaćeni dopust, SD=slobodan dan. Ako je u ćeliji druga kratka oznaka kao J, S, P1 ili broj 1/2/3, sačuvaj je doslovno i NE izmišljaj značenje. Prazna ćelija ne dobiva oznaku.

Ako je raspored fotografiran s monitora ili pod perspektivom, svejedno koristi geometriju tablice i redne brojeve. Posebno provjeri prvi i zadnji redak te dane 1 i zadnji dan mjeseca. expectedRows postavi na najveći pouzdano vidljiv broj redaka/raspon numeriranih redaka. Ne izmišljaj osobe, oznake ni dane. Ako nešto nije čitljivo, izostavi oznaku umjesto nagađanja.
PROMPT;

if ($monthHint !== '') {
    $prompt .= "\nKorisnički mjesec kao pomoćni hint: " . $monthHint . '.';
}
if ($localOcr !== '') {
    $prompt .= "\nLokalni OCR je dao sljedeći nesigurni tekst. Koristi ga samo kao pomoć; slika je izvor istine:\n" . $localOcr;
}

$schema = [
    'type' => 'object',
    'additionalProperties' => false,
    'required' => ['month', 'people', 'expectedRows', 'notes'],
    'properties' => [
        'month' => [
            'anyOf' => [
                [
                    'type' => 'object',
                    'additionalProperties' => false,
                    'required' => ['year', 'month'],
                    'properties' => [
                        'year' => ['type' => 'integer', 'minimum' => 2000, 'maximum' => 2100],
                        'month' => ['type' => 'integer', 'minimum' => 1, 'maximum' => 12],
                    ],
                ],
                ['type' => 'null'],
            ],
        ],
        'people' => [
            'type' => 'array',
            'maxItems' => 100,
            'items' => [
                'type' => 'object',
                'additionalProperties' => false,
                'required' => ['row', 'name', 'shifts'],
                'properties' => [
                    'row' => ['anyOf' => [['type' => 'integer', 'minimum' => 1, 'maximum' => 100], ['type' => 'null']]],
                    'name' => ['type' => 'string', 'minLength' => 1, 'maxLength' => 100],
                    'shifts' => [
                        'type' => 'array',
                        'maxItems' => 31,
                        'items' => [
                            'type' => 'object',
                            'additionalProperties' => false,
                            'required' => ['day', 'code'],
                            'properties' => [
                                'day' => ['type' => 'integer', 'minimum' => 1, 'maximum' => 31],
                                'code' => ['type' => 'string', 'minLength' => 1, 'maxLength' => 8],
                            ],
                        ],
                    ],
                ],
            ],
        ],
        'expectedRows' => ['type' => 'integer', 'minimum' => 0, 'maximum' => 100],
        'notes' => ['type' => 'string', 'maxLength' => 500],
    ],
];

$request = [
    'model' => $model,
    'store' => false,
    'input' => [[
        'role' => 'user',
        'content' => [
            ['type' => 'input_text', 'text' => $prompt],
            [
                'type' => 'input_image',
                'image_url' => 'data:' . $mime . ';base64,' . base64_encode($imageBytes),
                'detail' => 'high',
            ],
        ],
    ]],
    'text' => [
        'format' => [
            'type' => 'json_schema',
            'name' => 'raspored_schedule',
            'strict' => true,
            'schema' => $schema,
        ],
    ],
];

if (!function_exists('curl_init')) {
    ai_fail(503, 'cURL podrška nije dostupna na poslužitelju.');
}
$curl = curl_init('https://api.openai.com/v1/responses');
if ($curl === false) {
    ai_fail(503, 'AI vezu nije moguće pokrenuti.');
}
curl_setopt_array($curl, [
    CURLOPT_POST => true,
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_CONNECTTIMEOUT => 10,
    CURLOPT_TIMEOUT => 90,
    CURLOPT_HTTPHEADER => [
        'Authorization: Bearer ' . $apiKey,
        'Content-Type: application/json',
        'Accept: application/json',
    ],
    CURLOPT_POSTFIELDS => json_encode($request, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
]);

$responseBody = curl_exec($curl);
$status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
$error = curl_error($curl);
curl_close($curl);

if (!is_string($responseBody) || $responseBody === '' || $status < 200 || $status >= 300) {
    error_log('RASPORED AI OCR request failed; status=' . $status . '; curl=' . ($error !== '' ? 'yes' : 'no'));
    ai_fail(502, 'AI provjera nije uspjela. Lokalni OCR i dalje je dostupan.');
}

$response = json_decode($responseBody, true);
if (!is_array($response)) {
    ai_fail(502, 'AI odgovor nije valjan.');
}
$outputText = ai_extract_output_text($response);
$decoded = json_decode($outputText, true);
if (!is_array($decoded)) {
    ai_fail(502, 'AI odgovor nije moguće obraditi.');
}

$result = ai_clean_result($decoded);
echo json_encode(
    ['ok' => true, 'result' => $result],
    JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES
);
