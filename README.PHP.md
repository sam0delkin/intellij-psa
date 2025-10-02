# intellij-psa
## ![icon](src/main/resources/icons/pluginIcon_16.svg) Intellij Project-Specific Autocomplete

![Build](https://github.com/sam0delkin/intellij-psa/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/24604.svg)](https://plugins.jetbrains.com/plugin/24604)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/24604.svg)](https://plugins.jetbrains.com/plugin/24604)

## PHP PSA extension

For users of PhpStorm or IntelliJ IDEA Ultimate with PHP plugin installed, PSA provides some additional features for 
PHP:
- Type providers support
- Extending default xdebug `__toString` behavior

## Type Providers
### Idea
Intellij SDK for PHP provides an extension point where you can add new type providers which are changing the type of
value which is your variable/method is returning. So, for example, if you have some fabric which is returning some
classes of the single interface, but you want to show all methods of the specific class (which implementing the 
interface), you can do it using type providers. For example:
```php
interface SomeInterface {
    public function getName(): string;
}

class SomeClass1 implements SomeInterface {
    public function someClass1Method() {}
    public function getName(): string {
        return 'SomeClass1';
    }
}

class SomeClass2 implements SomeInterface {
    public function someClass2Method() {}
    public function getName(): string {
        return 'SomeClass2';
    }
}

class SomeFabric {
    public function get(string $name): SomeInterface {
        // ...
    }
}

// ...

$class = $someFabric->get('SomeClass1');
$class->someClass1Method(); // here PhpStorm (or IDEA) may say that `someClass1Method` is not defined
// also, IDE will show you that only `getName` method is available

```
So, Type Providers allows you to overhead this problem and change the return type of the fabric, based on the parameter
provider.

[//]: # (AI GENERATED BELOW)

### Type Provider Debug
When you enable PHP extension and turn on "Debug Type Provider" in Settings -> Tools -> PSA (PHP group), the plugin highlights the next token after a PSI element that matched one of your Type Provider patterns. This is helpful to verify that your pattern actually triggers where you expect it to.

- Where: Editor while typing PHP code
- How it looks: a yellow warning highlight with text like "PSA Type Provider: <ElementType>"
- When it appears: only when both the PSA plugin and PHP extension are enabled, the project has a valid PSA script configured, your PSA script reports supports_type_providers=true, and Debug Type Provider is enabled

### Providing Type Providers from your PSA script
PSA asks your script to provide Type Providers when it runs with PSA_TYPE=GetTypeProviders. Your script should return JSON that conforms to TypeProvidersModel with a list of TypeProviderModel items. See doc/schema.yaml for the full schema. In short, each provider contains:

- language: must be "PHP"
- pattern: a PsiElementPatternModel that identifies the PSI element to match
- type: a fully-qualified PHP type to set (e.g., "\\App\\Service\\SomeClass1")

Example response (using with_matcher):

```json
{
  "providers": [
    {
      "language": "PHP",
      "pattern": {
        "with_matcher": "element_type == 'METHOD_REFERENCE' && text == 'get' && next.text == '('"
      },
      "type": "\\App\\Service\\SomeClass1"
    }
  ]
}
```

Notes:
- Patterns are structural and operate on the PSI tree, not on raw text. Build them incrementally and use Debug Type Provider mode to validate.
- PSA caches providers per update; the list is refreshed whenever PSA runs Info and detects supports_type_providers=true.

### Enabling PHP extension
1. Open Settings -> Tools -> PSA.
2. In the PHP group:
   - Enable "Enabled" to turn the PHP extension on.
   - Optionally enable "Debug Type Provider" to visualize matches.
3. Ensure your global PSA settings are configured (script path, etc.).
4. Your PSA script's Info endpoint should report that PHP features are supported (see below).

### Making your script advertise PHP features (Info)
When PSA calls your script with PSA_TYPE=Info, return a PhpInfoModel-compatible JSON with these PHP-specific fields:

- supports_type_providers: boolean – set true to allow PSA to request type providers
- to_string_value_formatter: string – a snippet of PHP code that will be used to render values in the debugger (see next section)

Minimal example:

```json
{
  "supported_languages": ["PHP"],
  "supports_type_providers": true,
  "to_string_value_formatter": "return is_object($value) ? get_class($value) : (is_array($value) ? 'array(' . count($value) . ')' : (string)$value);"
}
```

### Xdebug value formatting (overriding __toString display)
When debugging with Xdebug, PSA can replace the default compact value shown for non-scalar values using your to_string_value_formatter from Info.

How it works:
- PSA wraps PHP debug values and, for non-scalar types, evaluates a small PHP snippet in the debug session to compute a short textual representation.
- The snippet walks from the root variable to the currently selected property/array element using reflection for private/protected properties, then calls your code as:
  (function ($value) { YOUR_CODE_HERE })($current)
- YOUR_CODE_HERE is exactly the string you return in to_string_value_formatter. It must use $value and return a string.

Example formatter code:

```php
return match (true) {
    is_array($value) => 'array(' . count($value) . ')',
    $value instanceof DateTimeInterface => $value->format(DATE_ATOM),
    is_object($value) => get_class($value),
    default => (string)$value,
};
```

Safety and notes:
- Errors inside the formatter are suppressed to avoid breaking the debug UI; if PSA debug notifications are enabled, evaluation errors will be shown.
- Formatter runs only if PHP extension is enabled and to_string_value_formatter is provided.
- Only non-scalar values are reformatted; scalars are shown as usual.

### Example project
See examples/php/example.php for a minimal setup demonstrating PSA with PHP. Use this as a starting point for crafting patterns and testing the formatter.
