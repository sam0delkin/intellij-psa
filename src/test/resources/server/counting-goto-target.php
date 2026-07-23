#!/usr/bin/env php
<?php

// Test fixture only - a persistent "Server" mode process that counts how many non-Info
// request lines it received (one file write per request) and replies to every one with a
// GoTo/Completion response pointing at a fixed target ("/target.php:1:7"). Used to test
// PsaReferenceContributor's live (non-static) reference resolution via the server.

$counterFile = __DIR__ . '/counting-goto-target.count';

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

    echo json_encode([
        'id' => $id,
        'result' => [
            'completions' => [
                ['text' => 'Target', 'link' => '/target.php:1:7'],
            ],
        ],
    ]) . "\n";
    flush();
}
