#!/usr/bin/env php
<?php

// Test fixture only - identical in shape to template-success.php, but returns a non-null value
// for the "notes" RichText field so tests can exercise the response -> UI sync-back path for a
// TextFieldWithCompletion component (SingleFileTemplateAction registers RichText fields in its
// `formFields` map, unlike MultipleFileTemplateAction).

echo json_encode([
    'file_name' => 'MyClass.php',
    'content' => "<?php\nclass MyClass {}\n",
    'form_fields' => [
        'className' => ['value' => 'MyClass', 'options' => []],
        'isAbstract' => ['value' => 'true', 'options' => []],
        'visibility' => ['value' => 'public', 'options' => []],
        'tags' => ['value' => null, 'options' => ['tag1', 'tag2']],
        'notes' => ['value' => 'Some generated notes', 'options' => ['note1']],
    ],
]);
