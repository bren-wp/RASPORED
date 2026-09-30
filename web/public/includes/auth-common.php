<?php
declare(strict_types=1);

function raspored_storage_directory(): string
{
    $sourceCandidate = dirname(__DIR__, 2) . '/storage/data';
    $flatPackageCandidate = dirname(__DIR__) . '/storage/data';
    $dir = is_dir($sourceCandidate) ? $sourceCandidate : $flatPackageCandidate;
    if (!is_dir($dir) && !mkdir($dir, 0700, true) && !is_dir($dir)) {
        throw new RuntimeException('Spremište nije dostupno.');
    }
    return $dir;
}

function raspored_cookie_path(): string
{
    $script = str_replace('\\', '/', (string) ($_SERVER['SCRIPT_NAME'] ?? '/'));
    $apiPos = strpos($script, '/api/');
    $base = $apiPos === false ? dirname($script) : substr($script, 0, $apiPos);
    if ($base === '' || $base === '.' || $base === '/') {
        return '/';
    }
    return rtrim($base, '/') . '/';
}

function raspored_is_https(): bool
{
    return (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off')
        || strtolower((string) ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '')) === 'https';
}

function raspored_start_session(): void
{
    if (session_status() === PHP_SESSION_ACTIVE) {
        return;
    }
    session_name('raspored_session');
    session_set_cookie_params([
        'lifetime' => 60 * 60 * 24 * 30,
        'path' => raspored_cookie_path(),
        'secure' => raspored_is_https(),
        'httponly' => true,
        'samesite' => 'Strict',
    ]);
    session_start();
}

function raspored_install_secret(): string
{
    $storageRoot = dirname(raspored_storage_directory());
    $path = $storageRoot . '/install-secret.php';

    $read = static function (string $file): string {
        if (!is_file($file)) {
            return '';
        }
        $secret = require $file;
        return is_string($secret) && preg_match('/^[a-f0-9]{64}$/', $secret) ? $secret : '';
    };

    $existing = $read($path);
    if ($existing !== '') {
        return $existing;
    }

    $lock = @fopen($storageRoot . '/.secret.lock', 'c');
    if ($lock === false || !flock($lock, LOCK_EX)) {
        if (is_resource($lock)) {
            fclose($lock);
        }
        throw new RuntimeException('Sigurnosna konfiguracija nije dostupna.');
    }

    try {
        $existing = $read($path);
        if ($existing !== '') {
            return $existing;
        }

        $secret = bin2hex(random_bytes(32));
        $tmp = $path . '.tmp-' . bin2hex(random_bytes(6));
        $payload = "<?php\ndeclare(strict_types=1);\nreturn '" . $secret . "';\n";
        if (@file_put_contents($tmp, $payload, LOCK_EX) === false) {
            @unlink($tmp);
            throw new RuntimeException('Sigurnosna konfiguracija nije moguća.');
        }
        @chmod($tmp, 0600);
        if (!@rename($tmp, $path)) {
            @unlink($tmp);
            throw new RuntimeException('Sigurnosna konfiguracija nije moguća.');
        }
        @chmod($path, 0600);
        return $secret;
    } finally {
        flock($lock, LOCK_UN);
        fclose($lock);
    }
}

function raspored_account_paths_from_email(string $email): array
{
    $normalized = strtolower(trim($email));
    $directory = raspored_storage_directory();

    return [
        'current' => $directory . '/account-' . hash_hmac('sha256', $normalized, raspored_install_secret()) . '.json',
        'legacy' => $directory . '/account-' . hash('sha256', $normalized) . '.json',
    ];
}

function raspored_account_path_from_email(string $email): string
{
    return raspored_account_paths_from_email($email)['current'];
}

function raspored_account_state_path(string $accountId): string
{
    return raspored_storage_directory() . '/account-state-' . hash('sha256', $accountId) . '.json';
}

function raspored_guest_state_path(string $token): string
{
    return raspored_storage_directory() . '/client-' . hash('sha256', $token) . '.json';
}

function raspored_current_account(): ?array
{
    raspored_start_session();
    $path = (string) ($_SESSION['account_path'] ?? '');
    $id = (string) ($_SESSION['account_id'] ?? '');
    if ($path === '' || $id === '' || !is_file($path)) {
        return null;
    }
    $raw = @file_get_contents($path);
    $account = json_decode((string) $raw, true);
    if (!is_array($account) || !hash_equals((string) ($account['id'] ?? ''), $id)) {
        return null;
    }
    return $account;
}

function raspored_public_account(?array $account): ?array
{
    if ($account === null) {
        return null;
    }
    return [
        'id' => (string) ($account['id'] ?? ''),
        'firstName' => (string) ($account['firstName'] ?? ''),
        'lastName' => (string) ($account['lastName'] ?? ''),
        'email' => (string) ($account['email'] ?? ''),
        'phone' => (string) ($account['phone'] ?? ''),
        'accountType' => (($account['accountType'] ?? 'individual') === 'manager') ? 'manager' : 'individual',
        'createdAt' => (string) ($account['createdAt'] ?? ''),
    ];
}

function raspored_same_origin_ok(): bool
{
    $origin = rtrim((string) ($_SERVER['HTTP_ORIGIN'] ?? ''), '/');
    if ($origin === '') {
        return true;
    }
    $expected = (raspored_is_https() ? 'https://' : 'http://') . (string) ($_SERVER['HTTP_HOST'] ?? '');
    return hash_equals(strtolower($expected), strtolower($origin));
}
