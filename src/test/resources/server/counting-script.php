#!/usr/bin/env php
<?php

// Test fixture only - a one-shot "Script" mode script that counts how many times it was
// invoked (one file write per process spawn), used to regression-test the fix for a bug where
// getStaticCompletions()/getTypeProviders() invoked the script twice per logical call.

$counterFile = __DIR__ . '/counting-script.count';
$count = is_file($counterFile) ? (int) file_get_contents($counterFile) : 0;
file_put_contents($counterFile, (string) ($count + 1));

echo json_encode(['static_completions' => [], 'providers' => []]);
