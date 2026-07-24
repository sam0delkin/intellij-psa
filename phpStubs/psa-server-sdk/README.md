# PSA Server SDK (PHP)

Plain PHP, zero Composer dependency. Gives you real autocomplete in PhpStorm while authoring a PSA
"server" - a persistent PHP process the plugin keeps alive across all requests (`Info`,
`Completion`, `GoTo`, ...), instead of spawning a fresh process per request the way the default
"Script" execution mode does.

**Important**: never `echo`/`print` anywhere in your script except through the values you `return`
from a handler (`Server::run()` turns those into the response line for you). The transport is
one JSON object per line (NDJSON) on stdout - any stray output (a `var_dump`, a PHP warning, a
leftover debug `echo`) will corrupt the stream, and the IDE will silently drop that line since it
can no longer be matched to the request that's waiting on it. Use `$server->log($message)` instead -
it writes to STDERR, which the IDE never reads as protocol data.

## Usage

```php
<?php

require_once __DIR__ . '/autoload.php';

use Psa\Server;
use Psa\InfoResponse;
use Psa\CompletionsResponse;
use Psa\CompletionModel;
use Psa\RequestContext;

$server = new Server();

$server->onInfo(function () {
    $info = new InfoResponse();
    $info->supportedLanguages = ['PHP'];
    $info->goToElementFilter = ['single quoted string', 'double quoted string'];

    return $info;
});

$server->onCompletion(function (RequestContext $request) {
    $response = new CompletionsResponse();

    if (($request->context['elementType'] ?? null) === 'single quoted string') {
        $completion = new CompletionModel();
        $completion->text = 'My Completion';
        $completion->priority = 123;
        $response->addCompletion($completion);
    }

    return $response;
});

$server->run();
```

Only `Info`, `Completion` and `GoTo` have typed helpers above. The other request types
(`GenerateFileFromTemplate`, `PerformEditorAction`, `GetStaticCompletions`, `GetTypeProviders`) are
registered the same way, e.g. `$server->on('GetStaticCompletions', function (RequestContext $request) { ... })`,
and should return a plain array in the same shape already documented in `doc/schema.yaml` for that
request type - the one-shot "Script" mode protocol and this one share the exact same payload shapes,
just a different transport. `PerformEditorAction` is the one exception: its handler should just
`return` a plain string (not an array) - `Server` encodes it as a JSON string automatically.

Note: `$request->offset` is `null` when there's no offset (unlike the one-shot `PSA_OFFSET` env var,
which uses `""` for the same case, since env vars are string-only).

## Reliability: the plugin keeps your server alive

The IDE starts the process proactively (on project open, not just on the first completion request)
and watches it: if it crashes, the plugin auto-restarts it with capped exponential backoff (a few
attempts, growing delay between each). If it gives up after those attempts, the status bar icon
turns red and a "Start Server" action becomes available in its popup menu (alongside
"Restart Server", always available) - your script doesn't need to implement any of this
itself, just handle requests and let the process exit/crash normally on unrecoverable errors.

## Refreshing state with `afterResponse()`

```php
$server->afterResponse(function (RequestContext $request) use ($server) {
    // Runs after EVERY response is sent (not just the first) - e.g. clear/warm caches here so
    // the *next* request sees fresh state (updated files, etc.), without a full server restart.
    // Runs synchronously in the request loop: it never delays the response that just went out,
    // but WILL delay the next request if it's slow (PHP has no free threading here) - keep it
    // fast, or fork (pcntl_fork) for real background work.
    $server->log("handled {$request->type} - refreshing caches");
});
```

## Sharing one script between Script mode and Server mode

When the plugin launches the process in Server mode, it sets `PSA_TYPE=StartServer`
as an env var (this happens once, at process startup - every request after that carries its own
`type` field in the NDJSON envelope instead, since env vars can't change mid-process). If you want a
single script file that works in both execution modes, check this env var before deciding whether to
build a `Server` and call `run()`, or fall through to the classic one-shot handling
(`getenv('PSA_TYPE')`, `getenv('PSA_CONTEXT')`, `echo json_encode(...)`, `exit`):

```php
<?php

require_once __DIR__ . '/../phpStubs/psa-server-sdk/autoload.php';

if (getenv('PSA_TYPE') === 'StartServer') {
    $server = new \Psa\Server();
    $server->onInfo(/* ... */);
    $server->onCompletion(/* ... */);
    $server->run();

    return;
}

// One-shot "Script" mode: handle a single request the classic way.
$type = getenv('PSA_TYPE');
$context = json_decode(@file_get_contents(getenv('PSA_CONTEXT')), true);
// ...
```
