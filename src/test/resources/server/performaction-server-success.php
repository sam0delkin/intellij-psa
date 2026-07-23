#!/usr/bin/env php
<?php

// Test fixture only - a persistent "Server" mode process that replies successfully to the
// health-check "Info" request and then echoes back a bare JSON string as the `result` for every
// other request, used to exercise PsaManager.performAction()'s Server-mode success path (which
// decodes the envelope's `result` as a plain JSON string, unlike other request types whose
// `result` is a structured object).

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
        echo json_encode(['id' => $id, 'result' => 'action-result']) . "\n";
    }
    flush();
}
