#!/usr/bin/env php
<?php

// Test fixture only - a one-shot "Script" mode script returning a CompletionsModel payload whose
// single completion links back to the test file itself, used to exercise
// AnyCompletionContributor.GotoDeclaration's script-fallback success path.

echo json_encode([
    'completions' => [
        ['text' => 'target', 'link' => '/test.php:1:1'],
    ],
]);
