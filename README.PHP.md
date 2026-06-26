# intellij-psa
## ![icon](src/main/resources/icons/pluginIcon_16.svg) PHP Extension

For users of PhpStorm or IntelliJ IDEA Ultimate with the PHP plugin installed, PSA provides additional PHP-specific
features on top of the general plugin capabilities:

- [Type Providers](#type-providers) — override return types of methods/factories based on PSI patterns
- [Xdebug value formatting](#xdebug-value-formatting) — custom `__toString`-style representation in the debugger variable panel
- [Method Argument Providers](#method-argument-providers) — GoTo, rename, change signature, parameter info, type checking, and inlay hints for dynamic PHP callables
- [Trait Structure View](#trait-structure-view) — groups trait-inherited methods by their originating trait in the Structure panel

---

## Enabling the PHP extension

1. Open **Settings → Tools → PSA**.
2. In the **PHP** group, check **Enabled**.
3. Ensure your PSA script path is configured and the global plugin is enabled.
4. Click the ℹ️ button next to the script path to reload info from your script.

---

## Type Providers

### What they do

PHP type providers let you override the return type that the IDE infers for a method call. This is useful for
factory/service-locator patterns where the declared return type is a broad interface, but you know the concrete class
at the call site based on a string argument.

```php
interface HandlerInterface {
    public function handle(): void;
}

class EmailHandler implements HandlerInterface {
    public function handle(): void {}
    public function getEmailAddress(): string { return ''; }
}

class SmsHandler implements HandlerInterface {
    public function handle(): void {}
    public function getPhoneNumber(): string { return ''; }
}

class HandlerFactory {
    public function get(string $name): HandlerInterface { /* … */ }
}

$factory = new HandlerFactory();
$handler = $factory->get('email');
// Without a type provider the IDE only knows HandlerInterface here.
// With a type provider matched on get('email') it knows EmailHandler,
// so getEmailAddress() is available with full completion and type safety.
$handler->getEmailAddress();
```

### Script response format

PSA calls your script with `PSA_TYPE=GetTypeProviders` when the Info response advertises
`"supports_type_providers": true`. Your script must return:

```json
{
  "providers": [
    {
      "language": "PHP",
      "pattern": { "<pattern fields>": "…" },
      "type": "\\Fully\\Qualified\\ClassName"
    }
  ]
}
```

Each `pattern` is a `PsiElementPatternModel` (same format used in static completions). The most flexible field is
`with_matcher`, which accepts an Apache Velocity expression evaluated against the PSI element:

```json
{
  "providers": [
    {
      "language": "PHP",
      "pattern": {
        "with_type": "METHOD_REFERENCE",
        "with_text": "get",
        "with_matcher": "$element.text == 'get' && $element.parent.parameters[0].contents == 'email'"
      },
      "type": "\\App\\Handler\\EmailHandler"
    }
  ]
}
```

You can also match structurally:

```json
{
  "providers": [
    {
      "language": "PHP",
      "pattern": {
        "with_type": "METHOD_REFERENCE",
        "with_options": {
          "options.resolve.model.options.FQN.string": "\\App\\HandlerFactory.get"
        },
        "any_parent": {
          "with_type": "Method reference",
          "any_parent": {
            "with_type": "Statement"
          }
        }
      },
      "type": "\\App\\Handler\\EmailHandler"
    }
  ]
}
```

### Advertising type provider support in Info

Your `Info` response must include `"supports_type_providers": true`:

```json
{
  "supported_languages": ["PHP"],
  "supports_type_providers": true
}
```

PSA fetches providers once on startup and again each time the Info response is reloaded (e.g. after clicking ℹ️ or
saving settings).

### Debug Type Provider mode

When **Debug Type Provider** is enabled in settings (PHP group), every PSI element that matches one of your provider
patterns is highlighted with a yellow annotation in the editor showing `PSA Type Provider: <ElementType>`. Use this to
verify patterns fire where you expect them to.

The annotation is visible only when:
- The plugin and PHP extension are both enabled.
- The script returns `"supports_type_providers": true`.
- **Debug Type Provider** is checked in settings.

---

## Xdebug value formatting

### What it does

When stepping through code with Xdebug, the Variables panel normally shows objects as a compact class name. PSA can
replace that representation by evaluating a small PHP snippet inside the live debug session, giving you a
project-specific one-line summary of each object.

### Providing a formatter

Return a `to_string_value_formatter` string in your `Info` response. Its value must be a PHP function body that uses
`$value` and returns a string:

```json
{
  "supported_languages": ["PHP"],
  "to_string_value_formatter": "return is_object($value) ? get_class($value) . '#' . spl_object_id($value) : (string)$value;"
}
```

A richer example:

```json
{
  "supported_languages": ["PHP"],
  "to_string_value_formatter": "return match (true) { is_array($value) => 'array(' . count($value) . ')', $value instanceof \\DateTimeInterface => $value->format(DATE_ATOM), is_object($value) => get_class($value), default => (string)$value };"
}
```

### How it works internally

For each non-scalar PHP debug value PSA evaluates:

```php
(function ($value) { YOUR_CODE_HERE })($current)
```

where `$current` is reached by walking from the root variable down to the selected property or array element using
reflection (including private/protected properties). Evaluation errors are suppressed silently to avoid breaking the
debugger UI; enable PSA debug notifications to see evaluation failures.

Scalars (strings, integers, floats, booleans, null) are never reformatted — they display as usual.

---

## Method Argument Providers

### What they do

Many PHP frameworks dispatch work through dynamic callables where the actual method being called is embedded in a
string literal or in a PHP callable array — for example:

```php
// Callable array pattern
$bus->dispatch(new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], [$account, $flush]));

// Separate class + method string pattern
$queue->executeServiceMethod(AccountStatsManager::class, 'updateStats', [$account, $flush]);
```

In both cases the IDE has no idea that `'updateStats'` refers to a real method. PSA Method Argument Providers teach the
IDE about this relationship so that the string literal gains full IDE support:

| IDE feature | What happens |
|---|---|
| **GoTo** (Ctrl+Click) | Navigates from the string `'updateStats'` to the method definition |
| **Rename** (Shift+F6 on the method) | Renames the string literal in all dynamic call sites automatically |
| **Change Signature** | When you add, remove, or reorder parameters of `updateStats`, PSA rewrites the arguments array to match |
| **Parameter Info** (Ctrl+P) | Shows the full parameter signature when the cursor is inside the arguments array |
| **Type checking** | Native PhpStorm "Expected parameter of type …" warning (with *Cast …* quick fixes) when an argument is incompatible with the resolved method parameter |
| **Inlay hints** | Renders the parameter name before each value in the arguments array |

### Providing providers in your Info response

Add a `method_argument_providers` array to your `Info` response. Each entry describes one call convention.

#### Pattern 1 — PHP callable array (`[ClassName::class, 'methodName']`)

Use this when the callable is passed as a two-element array where position 0 is the class and position 1 is the method
name string:

```php
// $bus->dispatch(new ServiceMethodMessage([ClassName::class, 'methodName'], $args));
//                                          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
//                                          callable at argument index 0 of __construct
//                                                                           ^^^^^^^^
//                                                                           arguments array at argument index 1
```

Info response:

```json
{
  "supported_languages": ["PHP"],
  "method_argument_providers": [
    {
      "class": "ServiceMethodMessage",
      "method": "__construct",
      "callable_argument_index": 0,
      "arguments_argument_index": 1,
      "arguments_offset": 0
    }
  ]
}
```

Field reference:

| Field | Type | Required | Description |
|---|---|---|---|
| `class` | string | yes | Short or FQN of the class whose constructor/method wraps the call |
| `method` | string | yes | Method name on that class (use `__construct` for `new` expressions) |
| `callable_argument_index` | integer | yes (for this pattern) | 0-based index of the argument that holds the `[Class, 'method']` array |
| `arguments_argument_index` | integer | yes | 0-based index of the argument that holds the dynamic method's argument array |
| `arguments_offset` | integer | no | Number of leading parameters to skip when mapping array positions to method parameters (default: 0) |

Full PHP example that works with this config:

```php
<?php

class ServiceMethodMessage
{
    public function __construct(array $callable, array $arguments) {}
}

class AccountStatsManager
{
    public function updateStats(Account $account, bool $flush = false): void {}
}

// 'updateStats' becomes a navigable reference; [$account, $flush] shows parameter info
new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], [$account, $flush]);
```

#### Pattern 2 — Separate class and method string arguments

Use this when the class name and method name are passed as separate string arguments to an ordinary method call:

```php
// $queue->executeServiceMethod('ClassName', 'methodName', $args);
//                               ^^^^^^^^^^^  ^^^^^^^^^^^^  ^^^^^^
//                               class at 0   method at 1   args at 2
```

Info response:

```json
{
  "supported_languages": ["PHP"],
  "method_argument_providers": [
    {
      "class": "QueueManager",
      "method": "executeServiceMethod",
      "class_argument_index": 0,
      "method_argument_index": 1,
      "arguments_argument_index": 2,
      "arguments_offset": 0
    }
  ]
}
```

Field reference:

| Field | Type | Required | Description |
|---|---|---|---|
| `class` | string | yes | Short or FQN of the class that owns the dispatcher method |
| `method` | string | yes | Name of the dispatcher method |
| `class_argument_index` | integer | yes (for this pattern) | 0-based index of the argument holding the target class name string |
| `method_argument_index` | integer | yes (for this pattern) | 0-based index of the argument holding the method name string |
| `arguments_argument_index` | integer | yes | 0-based index of the argument holding the dynamic method's argument array |
| `arguments_offset` | integer | no | Number of leading parameters to skip when mapping array positions to method parameters (default: 0) |

Full PHP example that works with this config:

```php
<?php

class QueueManager
{
    public function executeServiceMethod(string $class, string $method, array $args): void {}
}

class MyService
{
    public function doWork(int $x, int $y): void {}
}

$queue = new QueueManager();
// 'doWork' becomes a navigable reference; [1, 2] shows parameter info for doWork($x, $y)
$queue->executeServiceMethod('MyService', 'doWork', [1, 2]);
```

### The `arguments_offset` field

When the target method has some fixed leading parameters that are not included in the arguments array, set
`arguments_offset` to that count. For example, if `updateStats` always receives `$requestId` as its first
parameter from outside the dynamic call system, and only the remaining parameters are in the array:

```php
class AccountStatsManager
{
    // $requestId comes from context, not from the array
    public function updateStats(string $requestId, Account $account, bool $flush): void {}
}

// array only covers $account and $flush — offset the mapping by 1
new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], [$account, $flush]);
```

```json
{
  "callable_argument_index": 0,
  "arguments_argument_index": 1,
  "arguments_offset": 1
}
```

With `arguments_offset: 1`, parameter info shows `$account` at array position 0 and `$flush` at position 1, and
Change Signature inserts new parameters starting at array index `newParamIndex - 1`.

### Multiple providers

You can declare as many providers as needed. PSA tests each one independently:

```json
{
  "supported_languages": ["PHP"],
  "method_argument_providers": [
    {
      "class": "ServiceMethodMessage",
      "method": "__construct",
      "callable_argument_index": 0,
      "arguments_argument_index": 1,
      "arguments_offset": 0
    },
    {
      "class": "QueueManager",
      "method": "executeServiceMethod",
      "class_argument_index": 0,
      "method_argument_index": 1,
      "arguments_argument_index": 2,
      "arguments_offset": 0
    }
  ]
}
```

### Change Signature behaviour

When you use **Refactor → Change Signature** on a method that has dynamic call sites, PSA finds all matching
arrays and rewrites each one so that its values follow the new parameter order. The array is rebuilt from the new
parameter list:

- **Existing parameters** (those with an `oldIndex >= 0`) keep their original value, moved to the new position —
  so reordering or removing parameters is reflected in the array, not just additions.
- **New parameters** (`oldIndex == -1`) are inserted using the parameter's **default value** from the Change
  Signature dialog, or `null` if none was given.

The `arguments_offset` is honored throughout, so leading parameters that never appear in the array (see below) are
skipped when mapping parameter positions to array positions.

### Argument type checking

When the argument array resolves to a method, PSA reports incompatible argument types using **PhpStorm's own
type checker** — so you get the exact native experience: the standard *"Expected parameter of type '…', '…'
provided"* warning, the *Cast … to …* quick fixes, and full `declare(strict_types=1)` awareness.

```php
class AccountStatsManager
{
    public function updateStats(Account $account, bool $flush): void {}
}

// 1 is an int, not a bool — "Expected parameter of type 'bool', 'int' provided" with a "Cast 1 to bool" fix.
// An error if AccountStatsManager's file declares strict_types, otherwise a warning.
new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], [$account, 1]);
```

Because it delegates to the PHP plugin's checker, behaviour matches ordinary calls exactly:

- Any incompatibility is detected with PhpStorm's strict type checker — both hard mismatches (such as passing a
  wrong class) and scalar coercions PHP would reject under strict typing (e.g. `int → bool`).
- The mismatch is reported as an **error** when the target method's file declares `strict_types` (that method is
  meant to enforce strict typing), and as a **warning** otherwise.
- Only parameters with a declared type are checked; arguments whose type cannot be resolved are left alone.
- Variadic parameters apply their element type to every trailing value.
- In the editor the warning tooltip also shows the resolved method's signature, the same way PhpStorm previews
  the target of a real call.

The check is a standard inspection (**Settings → Editor → Inspections → PSA → "Incompatible argument type in
dynamic method call"**), so it can be configured or suppressed like any other. No script configuration is needed
beyond declaring the provider.

### Parameter name hints

PSA renders the parameter name before each value inside the arguments array, mirroring the inlay hints PhpStorm
shows for ordinary calls:

```php
new ServiceMethodMessage([AccountStatsManager::class, 'updateStats'], [/*account:*/ $account, /*flush:*/ true]);
```

Variadic parameters are shown as `...name:`. The hints appear as their own toggle under
**Settings → Editor → Inlay Hints → PHP → PSA dynamic method arguments**, so they can be disabled independently of
PhpStorm's native parameter hints.

---

## Trait Structure View

### What it does

When the PHP extension is enabled, the **Structure** panel (Alt+7 / View → Tool Windows → Structure) shows an
extra group for each trait used by the current class. Each group contains the methods contributed by that trait,
making it easy to navigate trait-inherited members without losing track of where they come from.

This feature works automatically — no script configuration is required. It activates as long as the PHP extension is
enabled in settings.

```php
<?php

trait Timestampable
{
    public function getCreatedAt(): \DateTimeImmutable { /* … */ }
    public function getUpdatedAt(): \DateTimeImmutable { /* … */ }
}

trait SoftDeletable
{
    public function softDelete(): void { /* … */ }
    public function restore(): void { /* … */ }
}

class Article
{
    use Timestampable;
    use SoftDeletable;

    public function getTitle(): string { return ''; }
}
```

In the Structure panel for `Article` you will see:

```
Article
├── getTitle()
├── Timestampable          ← trait group added by PSA
│   ├── getCreatedAt()
│   └── getUpdatedAt()
└── SoftDeletable          ← trait group added by PSA
    ├── softDelete()
    └── restore()
```

Traits used by other traits are resolved recursively, so deeply nested trait hierarchies are fully expanded.

### Enabling

No extra configuration is needed beyond enabling the PHP extension:

1. **Settings → Tools → PSA → PHP → Enabled** ✓
2. Open any PHP class that uses traits and open the Structure panel (Alt+7).
