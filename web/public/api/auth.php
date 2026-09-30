<?php
declare(strict_types=1);

require dirname(__DIR__) . '/includes/auth-common.php';

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, max-age=0');
header('Pragma: no-cache');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');
header('X-Frame-Options: DENY');

const AUTH_MAX_BODY = 16384;
const AUTH_WINDOW_SECONDS = 900;
const AUTH_MAX_ATTEMPTS = 12;

function auth_fail(int $status, string $message): never
{
    http_response_code($status);
    echo json_encode(['ok' => false, 'error' => $message], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function auth_text(mixed $value, int $max): string
{
    if (!is_string($value)) {
        return '';
    }
    $value = preg_replace('/\s+/u', ' ', trim($value)) ?? '';
    return function_exists('mb_substr') ? mb_substr($value, 0, $max, 'UTF-8') : substr($value, 0, $max);
}

function auth_length(string $value): int
{
    return function_exists('mb_strlen') ? mb_strlen($value, 'UTF-8') : strlen($value);
}

function auth_email(mixed $value): string
{
    $email = strtolower(trim((string) $value));
    return filter_var($email, FILTER_VALIDATE_EMAIL) ? $email : '';
}

function auth_phone(mixed $value): string
{
    $phone = preg_replace('/[^0-9+()\- .]/', '', trim((string) $value)) ?? '';
    return strlen(preg_replace('/\D/', '', $phone) ?? '') >= 7 ? substr($phone, 0, 30) : '';
}

function auth_rate_file(): string
{
    $ip = (string) ($_SERVER['REMOTE_ADDR'] ?? 'unknown');
    return raspored_storage_directory() . '/auth-rate-' . hash('sha256', $ip) . '.json';
}

function auth_check_rate_limit(bool $recordFailure = false): void
{
    $file = auth_rate_file();
    $now = time();
    $rows = [];
    if (is_file($file)) {
        $decoded = json_decode((string) @file_get_contents($file), true);
        if (is_array($decoded)) {
            $rows = array_values(array_filter($decoded, static fn ($v): bool =>
                is_int($v) && $v >= $now - AUTH_WINDOW_SECONDS
            ));
        }
    }
    if (count($rows) >= AUTH_MAX_ATTEMPTS) {
        auth_fail(429, 'Previše pokušaja. Pokušaj ponovno kasnije.');
    }
    if ($recordFailure) {
        $rows[] = $now;
        @file_put_contents($file, json_encode($rows), LOCK_EX);
        @chmod($file, 0600);
    }
}

function auth_write_account(string $path, array $account): void
{
    $lockPath = raspored_storage_directory() . '/.auth.lock';
    $lock = @fopen($lockPath, 'c');
    if ($lock === false || !flock($lock, LOCK_EX)) {
        if (is_resource($lock)) {
            fclose($lock);
        }
        auth_fail(503, 'Prijava trenutačno nije dostupna.');
    }
    try {
        if (is_file($path)) {
            auth_fail(409, 'Korisnički račun s tom e-mail adresom već postoji.');
        }
        $json = json_encode($account, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
        if ($json === false || @file_put_contents($path, $json . PHP_EOL, LOCK_EX) === false) {
            auth_fail(500, 'Korisnički račun nije moguće spremiti.');
        }
        @chmod($path, 0600);
    } finally {
        flock($lock, LOCK_UN);
        fclose($lock);
    }
}

function auth_migrate_guest_state(string $accountId): void
{
    $token = (string) ($_COOKIE['raspored_client'] ?? '');
    if (!preg_match('/^[a-f0-9]{64}$/', $token)) {
        return;
    }

    $guest = raspored_guest_state_path($token);
    $account = raspored_account_state_path($accountId);
    if (is_file($account) || !is_file($guest)) {
        return;
    }

    $lockPath = raspored_storage_directory() . '/.write.lock';
    $lock = @fopen($lockPath, 'c');
    if ($lock === false || !flock($lock, LOCK_EX)) {
        if (is_resource($lock)) {
            fclose($lock);
        }
        return;
    }

    try {
        if (is_file($account) || !is_file($guest)) {
            return;
        }
        if (@rename($guest, $account)) {
            @chmod($account, 0600);
            return;
        }
        if (@copy($guest, $account)) {
            @chmod($account, 0600);
            @unlink($guest);
        }
    } finally {
        flock($lock, LOCK_UN);
        fclose($lock);
    }
}

$mobile = raspored_mobile_client_request();
if ($mobile && !raspored_secure_api_transport_ok()) {
    auth_fail(403, 'Android sinkronizacija zahtijeva HTTPS.');
}
if (!$mobile) {
    raspored_start_session();
}
$method = strtoupper((string) ($_SERVER['REQUEST_METHOD'] ?? 'GET'));

if ($method === 'GET') {
    $account = $mobile
        ? raspored_mobile_account_from_token(raspored_bearer_token())
        : raspored_current_account();
    echo json_encode([
        'ok' => true,
        'authenticated' => $account !== null,
        'account' => raspored_public_account($account),
    ], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

if ($method !== 'POST') {
    header('Allow: GET, POST');
    auth_fail(405, 'Metoda nije dopuštena.');
}

if ($mobile) {
    if ((string) ($_SERVER['HTTP_X_RASPORED_REQUEST'] ?? '') !== '1') {
        auth_fail(403, 'Zahtjev nije dopušten.');
    }
} elseif ((string) ($_SERVER['HTTP_X_RASPORED_REQUEST'] ?? '') !== '1' || !raspored_same_origin_ok()) {
    auth_fail(403, 'Zahtjev nije dopušten.');
}

$contentLength = isset($_SERVER['CONTENT_LENGTH']) ? (int) $_SERVER['CONTENT_LENGTH'] : 0;
if ($contentLength > AUTH_MAX_BODY) {
    auth_fail(413, 'Zahtjev je prevelik.');
}
$body = file_get_contents('php://input', false, null, 0, AUTH_MAX_BODY + 1);
if ($body === false || strlen($body) > AUTH_MAX_BODY) {
    auth_fail(413, 'Zahtjev je prevelik.');
}
$payload = json_decode($body, true);
if (!is_array($payload)) {
    auth_fail(400, 'Neispravan JSON.');
}
$action = (string) ($payload['action'] ?? '');

if ($action === 'logout') {
    if ($mobile) {
        raspored_revoke_mobile_token(raspored_bearer_token());
        echo json_encode(['ok' => true, 'authenticated' => false], JSON_UNESCAPED_UNICODE);
        exit;
    }
    $_SESSION = [];
    if (ini_get('session.use_cookies')) {
        setcookie(session_name(), '', [
            'expires' => time() - 3600,
            'path' => raspored_cookie_path(),
            'secure' => raspored_is_https(),
            'httponly' => true,
            'samesite' => 'Strict',
        ]);
    }
    session_destroy();
    echo json_encode(['ok' => true, 'authenticated' => false], JSON_UNESCAPED_UNICODE);
    exit;
}

if ($action === 'register') {
    auth_check_rate_limit();
    $firstName = auth_text($payload['firstName'] ?? '', 60);
    $lastName = auth_text($payload['lastName'] ?? '', 60);
    $email = auth_email($payload['email'] ?? '');
    $phone = auth_phone($payload['phone'] ?? '');
    $password = (string) ($payload['password'] ?? '');
    $accountType = (($payload['accountType'] ?? 'individual') === 'manager') ? 'manager' : 'individual';

    if (auth_length($firstName) < 2 || auth_length($lastName) < 2) {
        auth_fail(422, 'Unesi valjano ime i prezime.');
    }
    if ($email === '') {
        auth_fail(422, 'Unesi valjanu e-mail adresu.');
    }
    if ($phone === '') {
        auth_fail(422, 'Unesi valjan broj telefona.');
    }
    if (strlen($password) < 10 || !preg_match('/[A-Za-zČĆŽŠĐčćžšđ]/u', $password) || !preg_match('/\d/', $password)) {
        auth_fail(422, 'Lozinka mora imati najmanje 10 znakova, slovo i broj.');
    }

    $paths = raspored_account_paths_from_email($email);
    $path = $paths['current'];
    if (is_file($paths['current']) || is_file($paths['legacy'])) {
        auth_fail(409, 'Korisnički račun s tom e-mail adresom već postoji.');
    }
    $id = bin2hex(random_bytes(16));
    $now = gmdate('c');
    $account = [
        'schema' => 1,
        'id' => $id,
        'firstName' => $firstName,
        'lastName' => $lastName,
        'email' => $email,
        'phone' => $phone,
        'accountType' => $accountType,
        'passwordHash' => password_hash($password, PASSWORD_DEFAULT),
        'createdAt' => $now,
        'updatedAt' => $now,
    ];
    auth_write_account($path, $account);
    $response = [
        'ok' => true,
        'authenticated' => true,
        'account' => raspored_public_account($account),
    ];
    if ($mobile) {
        try {
            $response += raspored_issue_mobile_token($path, $account);
        } catch (Throwable $error) {
            auth_fail(503, 'Android pristup trenutačno nije moguće aktivirati.');
        }
    } else {
        session_regenerate_id(true);
        $_SESSION['account_id'] = $id;
        $_SESSION['account_path'] = $path;
        auth_migrate_guest_state($id);
    }
    echo json_encode($response, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

if ($action === 'login') {
    auth_check_rate_limit();
    $email = auth_email($payload['email'] ?? '');
    $password = (string) ($payload['password'] ?? '');
    if ($email === '' || $password === '') {
        auth_fail(422, 'Unesi e-mail i lozinku.');
    }
    $paths = raspored_account_paths_from_email($email);
    $path = is_file($paths['current'])
        ? $paths['current']
        : (is_file($paths['legacy']) ? $paths['legacy'] : $paths['current']);
    $account = is_file($path) ? json_decode((string) @file_get_contents($path), true) : null;
    $valid = is_array($account)
        && isset($account['passwordHash'])
        && password_verify($password, (string) $account['passwordHash']);
    if (!$valid) {
        auth_check_rate_limit(true);
        usleep(180000);
        auth_fail(401, 'E-mail ili lozinka nisu ispravni.');
    }

    if ($path === $paths['legacy'] && !is_file($paths['current'])) {
        $lockPath = raspored_storage_directory() . '/.auth.lock';
        $lock = @fopen($lockPath, 'c');
        if ($lock !== false && flock($lock, LOCK_EX)) {
            try {
                if (!is_file($paths['current']) && is_file($paths['legacy'])
                    && @rename($paths['legacy'], $paths['current'])
                ) {
                    @chmod($paths['current'], 0600);
                    $path = $paths['current'];
                }
            } finally {
                flock($lock, LOCK_UN);
                fclose($lock);
            }
        } elseif (is_resource($lock)) {
            fclose($lock);
        }
    }

    $response = [
        'ok' => true,
        'authenticated' => true,
        'account' => raspored_public_account($account),
    ];
    if ($mobile) {
        try {
            $response += raspored_issue_mobile_token($path, $account);
        } catch (Throwable $error) {
            auth_fail(503, 'Android pristup trenutačno nije moguće aktivirati.');
        }
    } else {
        session_regenerate_id(true);
        $_SESSION['account_id'] = (string) $account['id'];
        $_SESSION['account_path'] = $path;
        auth_migrate_guest_state((string) $account['id']);
    }
    echo json_encode($response, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

auth_fail(400, 'Nepoznata radnja.');
