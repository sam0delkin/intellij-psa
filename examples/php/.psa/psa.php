<?php

$completions = [];
$notifications = [];

$context = json_decode(@file_get_contents(getenv('PSA_CONTEXT')), true);
$language = getenv('PSA_LANGUAGE');
$type = getenv('PSA_TYPE');

if ('Info' === $type) {
    echo json_encode([
        'supported_languages' => ['PHP'],
        'goto_element_filter' => [
            'single quoted string',
            'double quoted string',
        ],
        'templates' => [[
            'type' => 'single_file',
            'name' => 'my_awesome_template',
            'title' => 'My Awesome Template',
            'path_regex' => '^/src/[^/]+/$',
            'fields' => [
                [
                    'name' => 'className',
                    'title' => 'Class Name',
                    'type' => 'Text',
                    'options' => [],
                ],
                [
                    'name' => 'abstract',
                    'title' => 'Is Abstract',
                    'type' => 'Checkbox',
                    'options' => [],
                ],
                [
                    'name' => 'returnType',
                    'title' => 'Return Type',
                    'type' => 'Select',
                    'options' => ['TypeA', 'TypeB', 'TypeC']
                ],
                [
                    'name' => 'richText',
                    'title' => 'Rich Text with Completion',
                    'type' => 'RichText',
                    'options' => ['Completion A', 'Completion B', 'Completion C']
                ],
                [

                    'name' => 'collection',
                    'title' => 'Collection of text fields',
                    'type' => 'Collection',
                    'options' => []
                ]
            ],
        ]],
    ]);
    die;
}

if ('GenerateFileFromTemplate' === $type) {
    $templateName = $context['templateName'];
    $actionPath = $context['actionPath'];
    $formFields = $context['formFields'];
    $namespace = str_replace('src/', '', $actionPath);
    $namespace = implode('\\', explode('/', $namespace));

    $content = include_once (__DIR__ . '/templates/phpClass.template.php');

    echo json_encode([
        'file_name' => $formFields['className'] . '.php',
        'content' => $content,
        'form_fields' => [
            'richText' => [
                'options' => ['Completion A', 'Completion B', 'Completion C'],
            ],
        ],
    ]);

    exit(0);
}

if ($language === 'PHP') {
    if ($type === 'Completion') {
        if ($context['elementType'] === 'single quoted string') {
            $completions[] = [
                'text' => 'My Completion',
                'bold' => false,
                'priority' => 123,
                'type' => 'MyType',
            ];
        }
    }
    if ($type === 'GoTo') {
        $completions[] = [
            'link' => '/examples/php/psa.php:0:0',
        ];
    }

    $notifications[] = [
        'type' => 'info',
        'text' => 'Hello from my custom autocomplete!',
    ];
}

echo json_encode([
    'completions' => $completions,
    'notifications' => $notifications,
]);
