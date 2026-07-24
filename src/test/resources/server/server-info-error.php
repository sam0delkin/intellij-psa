#!/usr/bin/env php
<?php

// Test fixture only - a persistent "Server" mode process that launches and stays alive, but
// answers every request (including the health-check "Info" request) with an error envelope -
// used to exercise ServerManager.confirmHealthy()'s failure branch (a process that starts
// successfully but never reports itself healthy).

while (($line = fgets(STDIN)) !== false) {
    $line = trim($line);

    if ($line === '') {
        continue;
    }

    $request = json_decode($line, true);
    $id = $request['id'] ?? null;

    echo json_encode(['id' => $id, 'error' => 'not ready']) . "\n";
    flush();
}
