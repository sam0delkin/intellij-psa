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
        ]
    ]);
    die;
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
