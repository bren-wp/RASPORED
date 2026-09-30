<?php
declare(strict_types=1);

require dirname(__DIR__) . '/includes/auth-common.php';

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, max-age=0');
header('Pragma: no-cache');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');
header('X-Frame-Options: DENY');

const RASPORED_SCHEMA_VERSION = 4;
const RASPORED_MAX_BODY_BYTES = 2097152;

function default_state(): array
{
    return [
        'schema' => RASPORED_SCHEMA_VERSION,
        'revision' => 0,
        'schedule' => [],
        'evidence' => [],
        'profile' => ['name' => ''],
        'colleagues' => [],
        'teamMembers' => [],
        'settings' => ['theme' => 'light', 'reducedMotion' => false, 'notificationReadKey' => ''],
        'scanSession' => ['people' => [], 'selected' => -1, 'month' => null],
        'payroll' => [
            'county' => 'Primorsko-goranska',
            'residence' => 'Rijeka',
            'taxLower' => 20.0,
            'taxHigher' => 25.0,
            'sector' => 'Zdravstvo',
            'institution' => 'Klinički bolnički centar Rijeka',
            'regimeId' => 'kbc-rijeka-2026',
            'roleId' => 'health-transport-sss',
            'coefficient' => 1.25,
            'yearsService' => 0,
            'personalAllowance' => 600.0,
            'extraPercent' => 0.0,
            'secondShift' => false,
            'turnus' => false,
            'customBase' => null,
        ],
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

function storage_file(string $token, ?array $account): string
{
    if ($account !== null && isset($account['id'])) {
        return raspored_account_state_path((string) $account['id']);
    }
    return raspored_guest_state_path($token);
}

function text_slice(string $value, int $max): string
{
    return function_exists('mb_substr')
        ? mb_substr($value, 0, $max, 'UTF-8')
        : substr($value, 0, $max);
}

function text_length(string $value): int
{
    return function_exists('mb_strlen')
        ? mb_strlen($value, 'UTF-8')
        : strlen($value);
}

function clean_text(mixed $value, int $max): string
{
    if (!is_string($value)) {
        return '';
    }
    $value = preg_replace('/\s+/u', ' ', trim($value)) ?? '';
    return text_slice($value, $max);
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
            && in_array($code, ['D', 'N', 'GO', 'BO', 'PD', 'SD'], true)
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
    foreach (array_slice($raw, -3000) as $entry) {
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
        $workType = clean_text($entry['workType'] ?? 'regular', 20);
        if (!in_array($workType, ['regular', 'shift1', 'shift2', 'shift3', 'turnus', 'duty', 'standby', 'callout', 'other'], true)) {
            $workType = 'regular';
        }
        $clean[] = [
            'id' => clean_text((string) ($entry['id'] ?? ''), 80) ?: bin2hex(random_bytes(8)),
            'date' => $date,
            'in' => $in,
            'out' => $out === '' ? null : $out,
            'note' => clean_text($entry['note'] ?? '', 500),
            'workType' => $workType,
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
        if (text_length($name) < 2) {
            continue;
        }
        $clean[] = [
            'name' => $name,
            'note' => clean_text($item['note'] ?? '', 120),
        ];
    }
    return $clean;
}

function clean_team_members(mixed $raw): array
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
        if (text_length($name) < 2) {
            continue;
        }
        $schedule = clean_schedule($item['schedule'] ?? []);
        $clean[] = [
            'name' => $name,
            'note' => clean_text($item['note'] ?? '', 120),
            'schedule' => $schedule,
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
        if (text_length($name) < 2) {
            continue;
        }
        $shifts = [];
        $rawShifts = is_array($item['dayShifts'] ?? null) ? $item['dayShifts'] : [];
        foreach ($rawShifts as $day => $code) {
            $dayNumber = (int) $day;
            if ($dayNumber >= 1 && $dayNumber <= 31 && is_string($code) && in_array($code, ['D', 'N', 'GO', 'BO', 'PD', 'SD'], true)) {
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
    $payroll = is_array($raw['payroll'] ?? null) ? $raw['payroll'] : [];
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
        'teamMembers' => clean_team_members($raw['teamMembers'] ?? []),
        'settings' => [
            'theme' => (($settings['theme'] ?? 'light') === 'dark') ? 'dark' : 'light',
            'reducedMotion' => (bool) ($settings['reducedMotion'] ?? false),
            'notificationReadKey' => clean_text($settings['notificationReadKey'] ?? '', 120),
        ],
        'scanSession' => ['people' => $people, 'selected' => $selected, 'month' => $cleanMonth],
        'payroll' => [
            'county' => clean_text($payroll['county'] ?? 'Primorsko-goranska', 80) ?: 'Primorsko-goranska',
            'residence' => clean_text($payroll['residence'] ?? 'Rijeka', 100) ?: 'Rijeka',
            'taxLower' => max(0.0, min(50.0, is_numeric($payroll['taxLower'] ?? null) ? (float) $payroll['taxLower'] : 20.0)),
            'taxHigher' => max(0.0, min(50.0, is_numeric($payroll['taxHigher'] ?? null) ? (float) $payroll['taxHigher'] : 25.0)),
            'sector' => clean_text($payroll['sector'] ?? 'Zdravstvo', 100) ?: 'Zdravstvo',
            'institution' => clean_text($payroll['institution'] ?? 'Klinički bolnički centar Rijeka', 160) ?: 'Klinički bolnički centar Rijeka',
            'regimeId' => clean_text($payroll['regimeId'] ?? 'kbc-rijeka-2026', 80) ?: 'kbc-rijeka-2026',
            'roleId' => clean_text($payroll['roleId'] ?? 'health-transport-sss', 80) ?: 'health-transport-sss',
            'coefficient' => max(0.1, min(10.0, is_numeric($payroll['coefficient'] ?? null) ? (float) $payroll['coefficient'] : 1.25)),
            'yearsService' => max(0, min(60, is_numeric($payroll['yearsService'] ?? null) ? (int) $payroll['yearsService'] : 0)),
            'personalAllowance' => max(0.0, min(10000.0, is_numeric($payroll['personalAllowance'] ?? null) ? (float) $payroll['personalAllowance'] : 600.0)),
            'extraPercent' => max(0.0, min(100.0, is_numeric($payroll['extraPercent'] ?? null) ? (float) $payroll['extraPercent'] : 0.0)),
            'secondShift' => (bool) ($payroll['secondShift'] ?? false),
            'turnus' => (bool) ($payroll['turnus'] ?? false),
            'customBase' => is_numeric($payroll['customBase'] ?? null)
                ? max(0.0, min(10000.0, (float) $payroll['customBase']))
                : null,
        ],
        'updatedAt' => gmdate('c'),
    ];
}

function enforce_account_scope(array $state, ?array $account): array
{
    if ($account === null || (($account['accountType'] ?? 'individual') !== 'manager')) {
        $state['teamMembers'] = [];
    }

    if ($account !== null) {
        $fullName = clean_text(
            trim((string) ($account['firstName'] ?? '') . ' ' . (string) ($account['lastName'] ?? '')),
            80
        );
        if ($fullName !== '') {
            $state['profile']['name'] = $fullName;
        }
    }

    return $state;
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

function write_state(string $file, array $state, ?array $account): array
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
        $next = enforce_account_scope($next, $account);
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

function merge_state_patch(array $current, mixed $rawPatch): array
{
    if (!is_array($rawPatch)) {
        return $current;
    }
    foreach (['schedule', 'evidence', 'profile', 'colleagues', 'teamMembers', 'settings', 'payroll'] as $key) {
        if (array_key_exists($key, $rawPatch)) {
            $current[$key] = $rawPatch[$key];
        }
    }
    return $current;
}

$method = strtoupper((string) ($_SERVER['REQUEST_METHOD'] ?? 'GET'));
$mobile = raspored_mobile_client_request();
if ($mobile && !raspored_secure_api_transport_ok()) {
    fail_json(403, 'Android sinkronizacija zahtijeva HTTPS.');
}
$account = $mobile
    ? raspored_mobile_account_from_token(raspored_bearer_token())
    : raspored_current_account();
if ($mobile && $account === null) {
    fail_json(401, 'Prijava za Android sinkronizaciju nije valjana ili je istekla.');
}
$token = $account === null ? client_token() : '';
$file = storage_file($token, $account);

if ($method === 'GET') {
    $state = enforce_account_scope(read_state($file), $account);
    echo json_encode([
        'ok' => true,
        'state' => $state,
        'authenticated' => $account !== null,
        'account' => raspored_public_account($account),
    ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

if ($method !== 'PUT') {
    header('Allow: GET, PUT');
    fail_json(405, 'Metoda nije dopuštena.');
}

if ((string) ($_SERVER['HTTP_X_RASPORED_REQUEST'] ?? '') !== '1') {
    fail_json(403, 'Zahtjev nije dopušten.');
}

if (!$mobile) {
    $origin = rtrim((string) ($_SERVER['HTTP_ORIGIN'] ?? ''), '/');
    if ($origin !== '') {
        $secure = raspored_is_https();
        $expectedOrigin = ($secure ? 'https://' : 'http://') . (string) ($_SERVER['HTTP_HOST'] ?? '');
        if (!hash_equals(strtolower($expectedOrigin), strtolower($origin))) {
            fail_json(403, 'Izvor zahtjeva nije dopušten.');
        }
    }
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

$incoming = $payload['state'] ?? $payload;
if (($payload['patch'] ?? false) === true) {
    $incoming = merge_state_patch(read_state($file), $incoming);
}
$next = write_state($file, is_array($incoming) ? $incoming : [], $account);
echo json_encode([
    'ok' => true,
    'state' => $next,
    'authenticated' => $account !== null,
    'account' => raspored_public_account($account),
], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
