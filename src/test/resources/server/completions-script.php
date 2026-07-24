#!/usr/bin/env php
<?php

// Test fixture only - a one-shot "Script" mode script returning a minimal, strictly-shaped
// CompletionsModel JSON payload (no extra keys), used to exercise PsaManager.getCompletions()'s
// successful decode path.

echo json_encode([
    'completions' => [
        ['text' => 'myCompletion', 'type' => 'method'],
    ],
    'notifications' => [],
]);
