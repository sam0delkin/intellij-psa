#!/usr/bin/env php
<?php

// Test fixture only - a persistent "Server" mode process that replies successfully to the
// health-check "Info" request but omits `result` entirely (and reports no `error`) for every
// other request, used to exercise PsaManager.getCompletions()'s "No result received" branch -
// a live server that answers but sends neither a usable result nor an error message.

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
        echo json_encode(['id' => $id]) . "\n";
    }
    flush();
}
