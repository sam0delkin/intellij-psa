#!/usr/bin/env php
<?php

// Test fixture only - emits an extra malformed/non-JSON line before every real JSON response -
// used to exercise ServerManager.handleLine()'s catch for malformed lines, which must be silently
// ignored without disrupting matching of the subsequent, well-formed response line.

while (($line = fgets(STDIN)) !== false) {
    $line = trim($line);

    if ($line === '') {
        continue;
    }

    $request = json_decode($line, true);
    $id = $request['id'] ?? null;

    echo "this-is-not-json\n";
    flush();
    echo json_encode(['id' => $id, 'result' => []]) . "\n";
    flush();
}
