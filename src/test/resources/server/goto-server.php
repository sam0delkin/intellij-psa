#!/usr/bin/env php
<?php

// Test fixture only - a persistent "Server" mode process that replies to every non-Info
// request with a GoTo target linking back to the test file itself, used to exercise
// AnyCompletionContributor.GotoDeclaration.resolveLiveGoToTargets()'s success path.

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
        echo json_encode([
            'id' => $id,
            'result' => ['completions' => [['text' => 'target', 'link' => '/test.php:1:1']]],
        ]) . "\n";
    }
    flush();
}
