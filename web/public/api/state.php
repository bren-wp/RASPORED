<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, max-age=0');
header('Pragma: no-cache');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');
header('X-Frame-Options: DENY');

const RASPORED_SCHEMA_VERSION = 1;
const RASPORED_MAX_BODY_BYTES = 524288;

function default_state(): array
{
    return [
        'schema' => RASPORED_SCHEMA_VERSION,
        'revision' => 0,
        'schedule' => [],
        'evidence' => [],
        'profile' => ['name' => ''],
        'colleagues' => [],
        'settings' => ['theme' => 'light', 'reducedMotion' => false, 'notificationReadKey' => ''],
        'scanSession' => ['people' => [], 'selected' => -1, 'month' => null],
        'updatedAt' => null,
    ];
}

function fail_json(int $status, string $message): never
{
    http_response_code($status);
    echo json_encode(['ok' => false, 'error' => $message], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function client_cookie_path(): string
{
    $script = str_replace('\\', '/', (string) ($_SERVER['SCRIPT_NAME'] ?? '/api/state.php'));
    $apiDir = dirname($script);
    $base = dirname($apiDir);
    return $base === '.' || $base === '\\' ? '/' : rtrim($base, '/') . '/';
}

function client_token(): string
{
    $name = 'raspored_client';
    $token = (string) ($_COOKIE[$name] ?? '');
    if (!preg_match('/^[a-f0-9]{64}$/', $token)) {
        $token = bin2hex(random_bytes(32));
        $secure = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off')
            || ((string) ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https');
        setcookie($name, $token, [
            'expires' => time() + 31536000,
            'path' => client_cookie_path(),
            'secure' => $secure,
            'httponly' => true,
            'samesite' => 'Strict',
        ]);
    }
    return $token;
}

function storage_directory(): string
{
    $sourceCandidate = dirname(__DIR__, 2) . '/storage/data';
    $flatPackageCandidate = dirname(__DIR__) . '/storage/data';
    $dir = is_dir($sourceCandidate) ? $sourceCandidate : $flatPackageCandidate;
    if (!is_dir($dir) && !mkdir($dir, 0700, true) && !is_dir($dir)) {
        fail_json(500, 'Spremište nije dostupno.');
    }
    return $dir;
}

function storage_file(string $token): string
{
    return storage_directory() . '/client-' . hash('sha256', $token) . '.json';
}

function clean_text(mixed $value, int $max): string
{
    if (!is_string($value)) {
        return '';
    }
    $value = preg_replace('/\s+/u', ' ', trim($value)) ?? '';
    return mb_substr($value, 0, $max, 'UTF-8');
}

function clean_schedule(mixed $raw): array
{
    if (!is_array($raw)) {
        return [];
    }
    $clean = [];
    foreach ($raw as $date => $code) {
        if (is_string($date)
            && preg_match('/^\d{4}-\d{2}-\d{2}$/', $date)
            && is_string($code)
            && in_array($code, ['D', 'N', 'GO', 'BO'], true)
        ) {
            $clean[$date] = $code;
        }
        if (count($clean) >= 3660) {
            break;
        }
    }
    ksort($clean);
    return $clean;
}

function clean_evidence(mixed $raw): array
{
    if (!is_array($raw)) {
        return [];
    }
    $clean = [];
    foreach (array_slice($raw, -366) as $entry) {
        if (!is_array($entry)) {
            continue;
        }
        $date = (string) ($entry['date'] ?? '');
        $in = (string) ($entry['in'] ?? '');
        $out = $entry['out'] ?? null;
        if (!preg_match('/^\d{4}-\d{2}-\d{2}$/', $date)
            || !preg_match('/^\d{2}:\d{2}$/', $in)
            || ($out !== null && $out !== '' && (!is_string($out) || !preg_match('/^\d{2}:\d{2}$/', $out)))
        ) {
            continue;
        }
        $startedAt = isset($entry['startedAt']) && is_numeric($entry['startedAt']) ? (int) $entry['startedAt'] : null;
        $endedAt = isset($entry['endedAt']) && is_numeric($entry['endedAt']) ? (int) $entry['endedAt'] : null;
        $clean[] = [
            'id' => clean_text((string) ($entry['id'] ?? ''), 80) ?: bin2hex(random_bytes(8)),
            'date' => $date,
            'in' => $in,
            'out' => $out === '' ? null : $out,
            'note' => clean_text($entry['note'] ?? '', 500),
            'startedAt' => $startedAt,
            'endedAt' => $endedAt,
        ];
    }
    return $clean;
}

function clean_colleagues(mixed $raw): array
{
    if (!is_array($raw)) {
        return [];
    }
    $clean = [];
    foreach (array_slice($raw, 0, 30) as $item) {
        if (!is_array($item)) {
            continue;
        }
        $name = clean_text($item['name'] ?? '', 80);
        if (mb_strlen($name, 'UTF-8') < 2) {
            continue;
        }
        $clean[] = [
            'name' => $name,
            'note' => clean_text($item['note'] ?? '', 120),
        ];
    }
    return $clean;
}

function clean_scan_people(mixed $raw): array
{
    if (!is_array($raw)) {
        return [];
    }
    $clean = [];
    foreach (array_slice($raw, 0, 100) as $item) {
        if (!is_array($item)) {
            continue;
        }
        $name = clean_text($item['name'] ?? '', 100);
        if (mb_strlen($name, 'UTF-8') < 2) {
            continue;
        }
        $shifts = [];
        $rawShifts = is_array($item['dayShifts'] ?? null) ? $item['dayShifts'] : [];
        foreach ($rawShifts as $day => $code) {
            $dayNumber = (int) $day;
            if ($dayNumber >= 1 && $dayNumber <= 31 && is_string($code) && in_array($code, ['D', 'N', 'GO', 'BO'], true)) {
                $shifts[(string) $dayNumber] = $code;
            }
        }
        $row = isset($item['row']) && is_numeric($item['row']) ? (int) $item['row'] : null;
        $clean[] = ['row' => $row, 'name' => $name, 'dayShifts' => $shifts];
    }
    return $clean;
}

function clean_state(mixed $raw, int $revision): array
{
    $raw = is_array($raw) ? $raw : [];
    $settings = is_array($raw['settings'] ?? null) ? $raw['settings'] : [];
    $profile = is_array($raw['profile'] ?? null) ? $raw['profile'] : [];
    $scan = is_array($raw['scanSession'] ?? null) ? $raw['scanSession'] : [];
    $month = is_array($scan['month'] ?? null) ? $scan['month'] : null;
    $cleanMonth = null;
    if ($month !== null) {
        $year = (int) ($month['year'] ?? 0);
        $monthNumber = (int) ($month['month'] ?? 0);
        if ($year >= 2000 && $year <= 2100 && $monthNumber >= 1 && $monthNumber <= 12) {
            $cleanMonth = ['year' => $year, 'month' => $monthNumber];
        }
    }

    $people = clean_scan_people($scan['people'] ?? []);
    $selected = (int) ($scan['selected'] ?? -1);
    if ($selected < -1 || $selected >= count($people)) {
        $selected = -1;
    }

    return [
        'schema' => RASPORED_SCHEMA_VERSION,
        'revision' => max(0, $revision),
        'schedule' => clean_schedule($raw['schedule'] ?? []),
        'evidence' => clean_evidence($raw['evidence'] ?? []),
        'profile' => ['name' => clean_text($profile['name'] ?? '', 80)],
        'colleagues' => clean_colleagues($raw['colleagues'] ?? []),
        'settings' => [
            'theme' => (($settings['theme'] ?? 'light') === 'dark') ? 'dark' : 'light',
            'reducedMotion' => (bool) ($settings['reducedMotion'] ?? false),
            'notificationReadKey' => clean_text($settings['notificationReadKey'] ?? '', 120),
        ],
        'scanSession' => ['people' => $people, 'selected' => $selected, 'month' => $cleanMonth],
        'updatedAt' => gmdate('c'),
    ];
}

function read_state(string $file): array
{
    if (!is_file($file)) {
        return default_state();
    }
    $handle = @fopen($file, 'rb');
    if ($handle === false) {
        return default_state();
    }
    try {
        if (!flock($handle, LOCK_SH)) {
            return default_state();
        }
        $json = stream_get_contents($handle);
        flock($handle, LOCK_UN);
    } finally {
        fclose($handle);
    }
    $decoded = json_decode((string) $json, true);
    if (!is_array($decoded)) {
        return default_state();
    }
    $revision = isset($decoded['revision']) && is_numeric($decoded['revision']) ? (int) $decoded['revision'] : 0;
    return clean_state($decoded, $revision);
}

function write_state(string $file, array $state): array
{
    $dir = dirname($file);
    $lockPath = $dir . '/.write.lock';
    $lock = @fopen($lockPath, 'c');
    if ($lock === false || !flock($lock, LOCK_EX)) {
        if (is_resource($lock)) {
            fclose($lock);
        }
        fail_json(503, 'Spremište je trenutačno zauzeto.');
    }

    try {
        $current = read_state($file);
        $next = clean_state($state, ((int) ($current['revision'] ?? 0)) + 1);
        $json = json_encode($next, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
        if ($json === false) {
            fail_json(500, 'Podatke nije moguće kodirati.');
        }
        $tmp = $file . '.tmp-' . bin2hex(random_bytes(6));
        if (@file_put_contents($tmp, $json . PHP_EOL, LOCK_EX) === false) {
            @unlink($tmp);
            fail_json(500, 'Podatke nije moguće zapisati.');
        }
        @chmod($tmp, 0600);
        if (!@rename($tmp, $file)) {
            @unlink($tmp);
            fail_json(500, 'Podatke nije moguće dovršiti.');
        }
        @chmod($file, 0600);
        return $next;
    } finally {
        flock($lock, LOCK_UN);
        fclose($lock);
    }
}

$method = strtoupper((string) ($_SERVER['REQUEST_METHOD'] ?? 'GET'));
$token = client_token();
$file = storage_file($token);

if ($method === 'GET') {
    echo json_encode(['ok' => true, 'state' => read_state($file)], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

if ($method !== 'PUT') {
    header('Allow: GET, PUT');
    fail_json(405, 'Metoda nije dopuštena.');
}

if ((string) ($_SERVER['HTTP_X_RASPORED_REQUEST'] ?? '') !== '1') {
    fail_json(403, 'Zahtjev nije dopušten.');
}

$contentLength = isset($_SERVER['CONTENT_LENGTH']) ? (int) $_SERVER['CONTENT_LENGTH'] : 0;
if ($contentLength > RASPORED_MAX_BODY_BYTES) {
    fail_json(413, 'Podaci su preveliki.');
}

$body = file_get_contents('php://input', false, null, 0, RASPORED_MAX_BODY_BYTES + 1);
if ($body === false || strlen($body) > RASPORED_MAX_BODY_BYTES) {
    fail_json(413, 'Podaci su preveliki.');
}

$payload = json_decode($body, true);
if (!is_array($payload)) {
    fail_json(400, 'Neispravan JSON.');
}

$next = write_state($file, $payload['state'] ?? $payload);
echo json_encode(['ok' => true, 'state' => $next], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
