#!/usr/bin/env php
<?php

// Test fixture only - a one-shot "Script" mode script that branches on PSA_TYPE, used to exercise
// PsaStartupActivity's scheduled TimerTask end-to-end (both the Info call and the following
// updateStaticCompletions call it makes unconditionally).

$type = getenv('PSA_TYPE');

if ($type === 'GetStaticCompletions') {
    echo json_encode(['static_completions' => [], 'providers' => []]);
} else {
    echo json_encode([
        'supported_languages' => ['PHP'],
        'supports_static_completions' => true,
    ]);
}
