#!/usr/bin/env php
<?php

// Test fixture only - a persistent "Server" mode process that counts how many non-Info
// request lines it received (one file write per request), used to regression-test the fix for a
// bug where getStaticCompletions()/getTypeProviders() invoked the script twice per logical call.
// Info requests are excluded since ServerManager sends one on every process start to
// confirm the connection is live before reporting RUNNING.

$counterFile = __DIR__ . '/counting-server.count';

while (($line = fgets(STDIN)) !== false) {
    $line = trim($line);

    if ($line === '') {
        continue;
    }

    $request = json_decode($line, true);
    $id = $request['id'] ?? null;

    if (($request['type'] ?? null) !== 'Info') {
        $count = is_file($counterFile) ? (int) file_get_contents($counterFile) : 0;
        file_put_contents($counterFile, (string) ($count + 1));
    }

    echo json_encode(['id' => $id, 'result' => ['static_completions' => [], 'providers' => []]]) . "\n";
    flush();
}
