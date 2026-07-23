#!/usr/bin/env php
<?php

// Test fixture only - a one-shot "Script" mode script returning a full InfoModel payload used to
// exercise PsaConfigurable.getInfo()'s success path (fields, diagnostics, tooltip building).

echo json_encode([
    'supported_languages' => ['PHP', 'JavaScript'],
    'goto_element_filter' => ['PHP:STRING_LITERAL'],
    'supports_batch' => true,
    'templates' => [
        ['name' => 'my_template', 'type' => 'single_file', 'title' => 'My Template'],
    ],
    'supports_static_completions' => true,
    'editor_actions' => [],
]);
