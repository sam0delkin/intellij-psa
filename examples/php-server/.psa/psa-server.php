#!/usr/bin/env php
<?php

require_once __DIR__ . '/../../../phpStubs/psa-server-sdk/autoload.php';

use Psa\Server;
use Psa\InfoResponse;
use Psa\CompletionsResponse;
use Psa\CompletionModel;
use Psa\RequestContext;

$server = new Server();

// try to Ctrl/Command + Click on 'text' (GoTo)
// or try to Ctrl/Command + Space when your caret is between the single quote and `t` (Completion)

$server->afterResponse(function (RequestContext $request) use ($server) {
    $server->log("Handled {$request->type} - refreshing caches...");
});

$server->onInfo(function () {
    $info = new InfoResponse();
    $info->supportedLanguages = ['PHP'];
    $info->goToElementFilter = [
        'single quoted string',
        'double quoted string',
    ];

    return $info;
});

$server->onCompletion(function (RequestContext $request) {
    $response = new CompletionsResponse();

    if (($request->context['elementType'] ?? null) === 'single quoted string') {
        $completion = new CompletionModel();
        $completion->text = 'My Completion';
        $completion->priority = 123;
        $completion->type = 'MyType';
        $response->addCompletion($completion);
    }

    $response->addNotification('info', 'Hello from my custom autocomplete!');

    return $response;
});

$server->onGoTo(function (RequestContext $request) {
    $response = new CompletionsResponse();

    $link = new CompletionModel();
    $link->link = '/examples/php-server/.psa/psa-server.php:0:0';
    $response->addCompletion($link);

    $response->addNotification('info', 'Hello from my custom autocomplete!');

    return $response;
});

$server->run();
