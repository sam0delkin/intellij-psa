#!/usr/bin/env php
<?php

// Test fixture only - a one-shot "Script" mode script returning a minimal, strictly-shaped
// TemplateDataModel JSON payload for a multi-file template, used to exercise
// MultipleFileTemplateAction's successful template-generation UI update path.

echo json_encode([
    'file_names' => ['MyClass.php', 'MyClassTest.php'],
    'contents' => ["<?php\nclass MyClass {}\n", "<?php\nclass MyClassTest {}\n"],
    'form_fields' => [
        'className' => ['value' => 'MyClass', 'options' => []],
        'isAbstract' => ['value' => 'true', 'options' => []],
        'visibility' => ['value' => 'public', 'options' => []],
        'tags' => ['value' => null, 'options' => ['tag1', 'tag2']],
        'notes' => ['value' => null, 'options' => ['note1']],
    ],
]);
