#!/usr/bin/env php
<?php

// Test fixture only - a persistent "Server" mode process that replies successfully to the
// health-check "Info" request (so the manager reaches RUNNING) but answers every other
// request with an error envelope, used to exercise PsaManager.getCompletions() returning null
// from a live Server-mode call (as opposed to the server simply being unavailable).

while (($line = fgets(STDIN)) !== false) {
    $line = trim($line);

    if ($line === '') {
        continue;
    }

    $request = json_decode($line, true);
    $id = $request['id'] ?? null;

    if (($request['type'] ?? null) === 'Info') {
        echo json_encode(['id' => $id, 'result' => []]) . "\n";
    } else {
        echo json_encode(['id' => $id, 'error' => 'simulated failure']) . "\n";
    }
    flush();
}
