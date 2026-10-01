<?php
declare(strict_types=1);
header('Content-Type: text/html; charset=utf-8');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: strict-origin-when-cross-origin');
header('Permissions-Policy: camera=(self), microphone=(), geolocation=()');
header('X-Frame-Options: DENY');
header("Content-Security-Policy: default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; script-src 'self' https://cdn.jsdelivr.net 'wasm-unsafe-eval' 'unsafe-eval'; connect-src 'self' https://cdn.jsdelivr.net https://tessdata.projectnaptha.com; worker-src 'self' blob: https://cdn.jsdelivr.net; font-src 'self' data:");
$base = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? '/')), '/');
if ($base === '.') { $base = ''; }
$version = trim((string) @file_get_contents(dirname(__DIR__) . '/version.txt'));
if (!preg_match('/^\d+\.\d+\.\d+$/', $version)) { $version = '1.0.19'; }
