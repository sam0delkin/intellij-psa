#!/usr/bin/env php
<?php

// Test fixture only - a one-shot "Script" mode script returning a CompletionsModel payload with
// one completion and one notification of each type, used to exercise
// AnyCompletionContributor.Completion's script-fallback success path and notification processing.

echo json_encode([
    'completions' => [
        ['text' => 'myCompletion', 'type' => 'method', 'bold' => true],
    ],
    'notifications' => [
        ['type' => 'info', 'text' => 'info message'],
        ['type' => 'warning', 'text' => 'warning message'],
        ['type' => 'error', 'text' => 'error message'],
        ['type' => 'other', 'text' => 'other message'],
    ],
]);
