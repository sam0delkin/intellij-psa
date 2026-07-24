#!/usr/bin/env php
<?php

// Test fixture only - counts how many non-Info request lines it received and replies with an
// empty, well-formed Completion/GoTo response for every request.

$counterFile = __DIR__ . '/counting-empty-completions.count';

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

    echo json_encode(['id' => $id, 'result' => ['completions' => []]]) . "\n";
    flush();
}
