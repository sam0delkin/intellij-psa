#!/usr/bin/env php
<?php

// Test fixture only - a minimal NDJSON echo server used by ServerManagerTest.
// Responds with its own PID (to verify process reuse across requests), can be told to
// crash (exit non-zero) or sleep past a client timeout, on request. Also reports the
// PSA_TYPE env var it was launched with, to verify the manager sets it to StartServer.

$startupPsaType = getenv('PSA_TYPE');

while (($line = fgets(STDIN)) !== false) {
    $line = trim($line);

    if ($line === '') {
        continue;
    }

    $request = json_decode($line, true);
    $id = $request['id'] ?? null;
    $type = $request['type'] ?? null;

    if ($type === 'Crash') {
        exit(1);
    }

    if ($type === 'Sleep') {
        $ms = (int) ($request['context']['ms'] ?? 1000);
        usleep($ms * 1000);
    }

    echo json_encode([
        'id' => $id,
        'result' => ['pid' => getmypid(), 'type' => $type, 'startup_psa_type' => $startupPsaType],
    ]) . "\n";
    flush();
}
