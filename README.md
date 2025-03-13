# intellij-psa
## ![example](src/main/resources/icons/pluginIcon_16.svg) Intellij Project-Specific Autocomplete

![Build](https://github.com/sam0delkin/intellij-psa/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/24604.svg)](https://plugins.jetbrains.com/plugin/24604)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/24604.svg)](https://plugins.jetbrains.com/plugin/24604)


<!-- Plugin description -->
Small plugin which adds support for custom autocomplete & GoTo on the language you're writing your project.

Currently, supports:
* Custom autocomplete based on your code (Ctrl + Space)
* Custom GoTo based on your code (Ctrl/Command + Click)
* Custom code templates (with variables) based on your code
* Custom editor actions for convert parts of code to the needed format
* Static completions for faster experience

### Supported Languages
Any language that your IDE supports will be supported by plugin.

To check how to add custom autocomplete, GoTo and custom code templates, please read 
[documentation](https://github.com/sam0delkin/intellij-psa#documentation) 
<!-- Plugin description end -->

![example](doc/images/autocomplete_example.png)

Table of Contents
=================

* [Supported Languages](#supported-languages)
* [Installation](#installation)
* [Idea](#idea)
  * [Introduction](#introduction)
  * [How it works?](#how-it-works)
* [Documentation](#documentation)
  * [Swagger](#swagger)
  * [Configuration](#configuration)
  * [Custom autocomplete info](#custom-autocomplete-info)
  * [Completions &amp; GoTo](#completions--goto)
    * [Debug](#debug)
      * [Completions](#completions)
      * [GoTo](#goto)
  * [Static Completions](#static-completions)
  * [Code Templates](#code-templates)
    * [Single File Template](#single-file-template)
    * [Multiple File Template](#multiple-file-template)
  * [Editor Actions](#editor-actions)
  * [Performance considerations](#performance-considerations)
    * [General](#general)
    * [GoTo optimizations](#goto-optimizations)
  * [StatusBar Icon](#statusbar-icon)
* [Ideas / ToDo](#ideas--todo)
* [FAQ / How To](#faq--how-to)

## Installation

- Using IDE built-in plugin system:
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > 
  <kbd>Search for "Project Specific Autocomplete"</kbd> > <kbd>Install Plugin</kbd>

- Manually:

  Download the [latest release](https://github.com/sam0delkin/intellij-psa/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


## Idea
### Introduction

Most of scripting languages has own frameworks/CMS (like Symfony, Drupal in PHP, Nest, Next in JS/TS).
And there are some plugins to support some framework/CMS specific features, 
like [symfony plugin](https://plugins.jetbrains.com/plugin/7219-symfony-support)
 or [drupal plugin](https://plugins.jetbrains.com/plugin/7352-drupal). The main problem of
these plugins is that they are not project-specific, and you need to install a lot of plugins
for support different features. And what if you have some custom features in
your own project and want to handle references or autocomplete them?

### How it works?
First of all to use the plugin you should enable it in settings and provide a path to the **executable** script
which will be executed for completions or finding references for GoTo. 

Each time you will try to complete some expression (like ctrl + space) plugin will create a very simple representation
of the currently focused [PSI element](https://plugins.jetbrains.com/docs/intellij/psi-elements.html), JSON encode it,
write to a tmp file (caused by some limitations of arguments/ENV variable length) and pass it into the specified executable.

For example, if you try to autocomplete the following PHP code:
```php
<?php

function myFunc() {
    $l = '';
//        ^   caret is here
}

```
then you'll receive the following JSON in the filepath, passed from `PSA_CONTEXT` ENV variable:
<details>
  <summary>Expand</summary>

```JSON
{
  "elementType": "right single quote",
  "elementName": null,
  "elementFqn": null,
  "text": "'",
  "parent": {
    "elementType": "String",
    "elementName": null,
    "elementFqn": null,
    "text": "''",
    "parent": {
      "elementType": "Assignment expression",
      "elementName": null,
      "elementFqn": null,
      "text": "$l = ''",
      "parent": {
        "elementType": "Statement",
        "elementName": null,
        "elementFqn": null,
        "text": "$l = '';",
        "parent": {
          "elementType": "Group statement",
          "elementName": null,
          "elementFqn": null,
          "text": "{\n    $l = '';\n}",
          "parent": {
            "elementType": "FUNCTION",
            "elementName": null,
            "elementFqn": null,
            "text": "function myFunc() {\n    $l = '';\n}",
            "parent": {
              "elementType": "PsiElement(Non Lazy Group statement)",
              "elementName": null,
              "elementFqn": null,
              "text": "<?php\n\nfunction myFunc() {\n    $l = '';\n}",
              "parent": {
                "elementType": "php.FILE",
                "elementName": null,
                "elementFqn": null,
                "text": "<?php\n\nfunction myFunc() {\n    $l = '';\n}\n",
                "parent": {
                  "elementType": "<null>",
                  "elementName": null,
                  "elementFqn": null,
                  "text": "",
                  "parent": {
                    "elementType": "<null>",
                    "elementName": null,
                    "elementFqn": null,
                    "text": "",
                    "parent": {
                      "elementType": "<null>",
                      "elementName": null,
                      "elementFqn": null,
                      "text": "",
                      "parent": {
                        "elementType": "<null>",
                        "elementName": null,
                        "elementFqn": null,
                        "text": "",
                        "parent": {
                          "elementType": "<null>",
                          "elementName": null,
                          "elementFqn": null,
                          "text": "",
                          "parent": {
                            "elementType": "<null>",
                            "elementName": null,
                            "elementFqn": null,
                            "text": "",
                            "parent": null,
                            "prev": null,
                            "next": null
                          },
                          "prev": {
                            "elementType": "<null>",
                            "elementName": null,
                            "elementFqn": null,
                            "text": "",
                            "parent": null,
                            "prev": null,
                            "next": null
                          },
                          "next": {
                            "elementType": "<null>",
                            "elementName": null,
                            "elementFqn": null,
                            "text": "",
                            "parent": null,
                            "prev": null,
                            "next": null
                          }
                        },
                        "prev": null,
                        "next": {
                          "elementType": "PLAIN_TEXT_FILE",
                          "elementName": null,
                          "elementFqn": null,
                          "text": "",
                          "parent": null,
                          "prev": null,
                          "next": null
                        }
                      },
                      "prev": {
                        "elementType": "<null>",
                        "elementName": null,
                        "elementFqn": null,
                        "text": "",
                        "parent": null,
                        "prev": null,
                        "next": null
                      },
                      "next": {
                        "elementType": "<null>",
                        "elementName": null,
                        "elementFqn": null,
                        "text": "",
                        "parent": null,
                        "prev": null,
                        "next": null
                      }
                    },
                    "prev": {
                      "elementType": "<null>",
                      "elementName": null,
                      "elementFqn": null,
                      "text": "",
                      "parent": null,
                      "prev": null,
                      "next": null
                    },
                    "next": {
                      "elementType": "<null>",
                      "elementName": null,
                      "elementFqn": null,
                      "text": "",
                      "parent": null,
                      "prev": null,
                      "next": null
                    }
                  },
                  "prev": {
                    "elementType": "<null>",
                    "elementName": null,
                    "elementFqn": null,
                    "text": "",
                    "parent": null,
                    "prev": null,
                    "next": null
                  },
                  "next": {
                    "elementType": "<null>",
                    "elementName": null,
                    "elementFqn": null,
                    "text": "",
                    "parent": null,
                    "prev": null,
                    "next": null
                  }
                },
                "prev": null,
                "next": null
              },
              "prev": {
                "elementType": "WHITE_SPACE",
                "elementName": null,
                "elementFqn": null,
                "text": "\n",
                "parent": null,
                "prev": null,
                "next": null
              },
              "next": null
            },
            "prev": null,
            "next": {
              "elementType": "WHITE_SPACE",
              "elementName": null,
              "elementFqn": null,
              "text": "\n\n",
              "parent": null,
              "prev": null,
              "next": null
            }
          },
          "prev": null,
          "next": {
            "elementType": "WHITE_SPACE",
            "elementName": null,
            "elementFqn": null,
            "text": " ",
            "parent": null,
            "prev": null,
            "next": null
          }
        },
        "prev": {
          "elementType": "WHITE_SPACE",
          "elementName": null,
          "elementFqn": null,
          "text": "\n",
          "parent": null,
          "prev": null,
          "next": null
        },
        "next": {
          "elementType": "WHITE_SPACE",
          "elementName": null,
          "elementFqn": null,
          "text": "\n    ",
          "parent": null,
          "prev": null,
          "next": null
        }
      },
      "prev": {
        "elementType": "semicolon",
        "elementName": null,
        "elementFqn": null,
        "text": ";",
        "parent": null,
        "prev": null,
        "next": null
      },
      "next": null
    },
    "prev": null,
    "next": {
      "elementType": "WHITE_SPACE",
      "elementName": null,
      "elementFqn": null,
      "text": " ",
      "parent": null,
      "prev": null,
      "next": null
    }
  },
  "prev": null,
  "next": {
    "elementType": "left single quote",
    "elementName": null,
    "elementFqn": null,
    "text": "'",
    "parent": null,
    "prev": null,
    "next": null
  }
}
```
</details>

> [!NOTE]
> In the output above the `options` and `textRange` options are omitted to make output less size.

## Documentation

### Swagger

You can check the swagger UI, generated by the Models within the plugin classes.
It can be used for better understanding of how plugin works, plus for generation of a
DTO classes for PSA.

See [Swagger](https://swagger.io/) documentation [here](https://petstore.swagger.io/?url=https://raw.githubusercontent.com/sam0delkin/intellij-psa/refs/heads/main/doc/doc.yaml).

> [!NOTE]
> All methods described in the Swagger doc are "fake" methods, and listed only to describe structure of calls which will
> be made to your PSA script.


Also, check out the [OpenAPI Generator](https://openapi-generator.tech/) documentation for more details about of
generation DTO classes for PSA.

### Configuration
![settings](doc/images/settings.png)
1) Is plugin enabled or not.
2) Is debug enabled or not. Passed as `PSA_DEBUG` into the executable script.
3) Path to the PSA executable script. Must be an executable file.
4) ![info](doc/images/balloonInformation.svg) button to update info from your PSA script. 
5) Maximum script execution timeout. If script will execute longer that this value, execution will be interrupted.
6) Path mappings (for projects that running remotely (within Docker/Vagrant/etc.)). Source mapping should start from `/`
as project root. 
7) Programming languages supported by your autocompletion. Separated by comma, read-only.
8) ![info](doc/images/balloonInformation.svg) button to get all the languages supported by your IDE. Comma separated. 
Only one of these languages allowed to be passed in `supported_languages` value. 
9) GoTo element filter returned by you autocompletion. Separated by comma, read-only. Read more in 
[performance](#goto-optimizations) section.

To configure your autocomplete, follow these actions:
1) Check the `Plugin Enabled` checkbox (1) for enable plugin
2) Specify a path to your executable in the `Script Path` field (3)
3) Click the ![info](doc/images/balloonInformation.svg) icon right to the `Script Path` field to retrieve info from your executable
4) After that fields `Supported Languages` (5) and optionally `GoTo Element Filter` (6) will be filled automatically in
case of your script is return data in valid format
5) Save settings

### Custom autocomplete info

When you installed and enabled plugin, and you click the ![info](doc/images/balloonInformation.svg) icon right to the `Script Path` field, IDE will run
your executable script to retrieve supported languages + GoTo element filter (for 
[performance optimizations](#goto-optimizations)). In this case only 2 ENV variables would be passed to your executable:
* `PSA_TYPE` - will be `Info`.
* `PSA_DEBUG` - `1` in case of debug is enabled in plugin settings and `0` otherwise.

As a result, your script should return an array of supported languages:
<details>
  <summary>Expand</summary>

```JSON
{
  "supported_languages": "array of strings. List of supported programming languages.",
  "goto_element_filter": "optional, array of strings. Used for filter element types where GoTo will work. Performance optimization.",
  "templates": [
    {
      "type": "string, required. `single_file` or `multiple_file` are supported.",
      "name": "string, required. Name of the template for reference. Will be passed in `PSA_CONTEXT` during template generation.",
      "title": "string, required. Title of the template. This text will be shown in IDE.",
      "path_regex": "string, optional. Regular expression. Used to filer path where this code template is available.",
      "fields": [
        {
          "name": "string, required. Name of the form field. Will be passed in `PSA_CONTEXT` during template generation.",
          "title": "string, required. Title of the field which will be displayed in form.",
          "type": "string, required. Allowed values are `Text`, `Checkbox`, `Select`. Type of the form field.",
          "options": "array of strings. Required only if `type` is `Select`. Array of select options."
        }
      ]
    }
  ],
  "supports_batch": "optional, boolean. Specifies does batch processing is supported by your script. Useful for speed-up indexing."
}
```
</details>

For example, if your script is supporting JavaScript and TypeScript and return GoTo only for JS string literals, you
should return the following JSON:
<details>
  <summary>Expand</summary>

```JSON
{
  "supported_languages": ["JavaScript", "TypeScript"],
  "goto_element_filter": ["JS:STRING_LITERAL"]
}
```
</details>

### Completions & GoTo

As it already mentioned in [introduction](#how-it-works), plugin is sending JSON-encoded PSI tree into the executable.

Here is the full list of ENV variables passed to the executable:
* `PSA_CONTEXT` - file path that contain the JSON-encoded PSI context.
* `PSA_TYPE` - may be either `Completion` or `GoTo`. Type of the execution.
* `PSA_LANGUAGE` - language which is caused the autocomplete/resolving reference (`PHP`, `JS`, ...).
* `PSA_DEBUG` - `1` in case of debug is enabled in plugin settings and `0` otherwise.
* `PSA_OFFSET` - shows cursor position within current element in editor.

So, you can parse the JSON and analyze it for your needs. This JSON has a tree structure, and each element will have
the following structure:

<details>
  <summary>Expand</summary>

```JSON
{
  "elementType": "string",
  "elementName": "string | null",
  "elementFqn": "string | null",
  "options": {
    "optionName": "optionValue"
  },
  "text": "string",
  "parent": "additional tree element",
  "prev": "additional tree element",
  "next": "additional tree element",
  "textRange": {
    "startOffset": "integer, start PSI element position is the file",
    "endOffset": "integer, end PSI element position is the file"
  }
}
```
</details>

By analyzing element and it's parents + some options you may find how to check that the caret is on the element
which may be autocompleted.

As a result your script should return:

* Array of completions in case of `PSA_TYPE` is `Completion`.
* Array of completions with one element (this element should contain a link) in case of `PSA_TYPE` is `GoTo`.
* Optional, you can pass notifications array which will be shown by IDEA. Useful for debug purposes.
* Optional, you can return an array of element types to filter GoTo for performance reasons. For more information, 
please read [performance](#goto-optimizations) section.

Full resulting JSON structure will be described below:

<details>
  <summary>Expand</summary>

```JSON
{
  "completions": [
    {
      "text": "string, the text of completion",
      "link": "string, required only in case of `PSA_TYPE=GoTo`, the absolute/relative link to the file in format FileName.ext[:line_number][:position]",
      "bold": "boolean, should the completion be bold.",
      "priority": "number, optional. Used for ordering elements in the autocomplete. If `bold` is `true` and `priority` is not specified, then default value would be 100.",
      "type": "string, the type which will be shown as grayed text on the right of completion."
    }
  ],
  "notifications": [
    {
      "type": "string, may be either `info`, `error` or `warning`.",
      "text": "string, the text of the notification."
    }
  ]
}
```
</details>

And the full working example:

<details>
  <summary>Expand</summary>

```JSON
{
  "completions": [
    {
      "text": "My Completion",
      "link": "/path/to/file.php:123:123",
      "bold": false,
      "priority": 123,
      "type": "MyType"
    }
  ],
  "notifications": [
    {
      "type": "info",
      "text": "Hello from my custom autocomplete!"
    }
  ]
}
```
</details>

In case of your executable will respond with the JSON above, result completion will look like:

![example](doc/images/full_message_1.png)

And the following notification will be shown:

![example](doc/images/notification_example.png)

For working examples on different languages, check out the [examples](examples) folder.

> [!NOTE]
> In case of `PSA_TYPE` is `GoTo`, you should return only one completion with the link to reference.

#### Debug

It's almost impossible to describe the full structure of `PSA_CONTEXT`, especially all `options` passed to the context,
due to its very dynamic and based on the language you're using. Of course, you can just write JSON into the tmp file
and then analyze it, but it's much easier to use debug on your language. When `Debug` option is set in the plugin
settings, a ENV variable `PSA_DEBUG` will be passed to your script with value `1`. You can use it for debugging. 

You can always execute your script with debug option, but it will slow down the execution during the time you're not 
need to debug autocomplete. For this purpose a `PSA_DEBUG` option is passing to your script. Some examples for 
[PHP](examples/php/psa.sh), [JavaScript](examples/js/psa.sh), [TypeScript](examples/ts/psa.sh) are shown in the 
[examples](examples) folder.

##### Completions

When `Debug` option is set in the plugin settings, and you try to autocomplete something in some supported language,
debug will break on your breakpoint (if specified). So you can debug autocomplete script like you usually debugging your
app (on PHP, JavaScript, TypeScript, etc.). See examples for PHP and JavaScript:

<details>
  <summary>PHP Debug</summary>

![example_debug_php_invoke](doc/images/debug_php_invoke.png)
![example_debug_php](doc/images/debug_php.png)
</details>

<details>
  <summary>JavaScript Debug</summary>

![example_debug_js_invoke](doc/images/debug_js_invoke.png)
![example_debug_js](doc/images/debug_js.png)
</details>


##### GoTo

GoTo debugging is working absolutely same as completion debugging, except one thing: when IDE is running completion,
execution may be interrupted (user may click on other element, or press Escape key), and it means that it's ok to run
long command during autocomplete and IDE will not freeze during this execution. But resolving reference can't be
interrupted and executing synchronously, so when you will try to run GoTo with debug enabled, you'll see the following
window:

![resolving_reference](doc/images/resolving_reference.png)

And when you press `Cancel`, you'll see the following window:

![operation_too_complex](doc/images/operation_too_complex.png)

It's ok, and when you'll press `OK`, you will be able to debug your completion and execution will be stopped on 
breakpoint.

> [!NOTE]
> To not overload IDE, plugin automatically check that completion is started in any file within the directory of
> `Script Path` setting and prevent GoTo/Completion from any file of this path. So, debug sessions will not be
> recursively started during your debugging session.

> [!WARNING]
> Keep `Debug` option disabled in plugin settings such as it has a strong impact on performance. Enable debug only in
> case of you want to debug your autocomplete (write new completion, or check why some old is not working).

### Static Completions

In case of your completions are not so dynamic and specific, you can use a static completions which are working much
faster than usual completions. To enable static completions, you should set `supports_static_completions` to `true`
in your executable script `Info` request result. After that, plugin will call an additional request to your script
with `PSA_TYPE` = `GetStaticCompletions`. Your script should return JSON with the following structure:

<details>
  <summary>Expand</summary>

```JSON
{
  "static_completions": [
    {
      "name": "string",
      "title": "string",
      "patterns": [
        {
          "parent": "string",
          "prev": "string",
          "next": "string",
          "with_text": "string",
          "with_type": "string",
          "with_options": {
            "additionalProp1": "string",
            "additionalProp2": "string",
            "additionalProp3": "string"
          },
          "any_parent": "string",
          "any_prev": "string",
          "any_next": "string"
        }
      ],
      "completions": {
        "completions": [
          {
            "text": "string",
            "bold": true,
            "type": "string",
            "priority": 0,
            "link": "string",
            "presentable_text": "string",
            "tail_text": "string"
          }
        ],
        "notifications": [
          {
            "type": "string",
            "text": "string"
          }
        ]
      }
    }
  ]
}
```
</details>

Here is the new tricky thing, called `patterns` field. It's an array of objects which are representing a pattern to
match PSI elements to autocomplete and goto. Plugin will match PSI element to the first pattern which matches.

To help with building such patterns, a new action were added to the File Editor menu, called `Generate Pattern Model`:

![generate_pattern_model](doc/images/generate_pattern_model.png)

After using of this action, plugin will get the element at a cursor, and generate a full pattern model which will
be used for matching elements for autocomplete and goto (JSON of the pattern will be copied to a clipboard). You will 
just need to remove unnecessary matchers, and that's it. See example in PHP:

<details>
  <summary>Expand</summary>

```JSON
{
  "with_type": "single quoted string",
  "parent": {
    "parent": {
      "parent": {
        "with_type": "Method reference",
        "with_options": {
          "options.resolve.model.elementType": "CLASS_METHOD",
          "options.resolve.model.options.FQN.string": "\\App\\Some\\Name\\SomeClass.someMethod"
        }
      }
    }
  }
}

```
</details>

The example above will match to any calls like:
```php
// private App\Some\Name\SomeClass $someClass;

$this->someClass->someMethod('name');
//                            ^
//                            |
```

### Code Templates

Most of the languages provides some general file templates, like `PHP Class` in PHP or `TypeScript File`
in TypeScript. Plugin allows you to create custom file templates which will have variables passed from form.
For support of file templates you must specify all supported templates in your executable script in
`templates` section. Check out [autocomplete info](#custom-autocomplete-info) section for more info.

#### Single File Template

In case of you need to create a single file template, in info request your JSON should contain template with the
following fields:
- `type` - string, required. `single_file` or `multiple_file` are supported. For single file template pass `single_file`
as a value.
- `name` - string, required. Name of the template for reference. Will be passed in `PSA_CONTEXT` during template generation.
- `title` - string, required. Title of the template. This text will be shown in IDE.
- `path_regex` - string, optional. Regular expression to filter paths where template creation action will be available.
- `fields` - array of objects with the following structure:
  - `name` - string, required. Name of the form field. Will be passed in `PSA_CONTEXT` during template generation.
  - `title` - string, required. Title of the field which will be displayed in form.
  - `type` - string, required. Allowed values are `Text`, `Checkbox`, `Select`, `Collection`, `RichText`. Type of the form field.
  - `focused` - boolean, optional. Set to true for the field you want to be focused on opening template creation dialog.
  - `options` - array of strings.
    - Required if `type` is `Select`. Array of select options.
    - Required if `type` is `RichText`. Array of completions.

For example, for some simple PHP Class you can use the following structure:
<details>
  <summary>Expand</summary>

```JSON
{
  "templates": [
    {
      "type": "single_file",
      "name": "my_awesome_template",
      "title": "My Awesome Template",
      "path_regex": "^\/src\/[^\/]\/$",
      "fields": [
        {
          "name": "className",
          "title": "Class Name",
          "type": "Text",
          "options": []
        },
        {
          "name": "abstract",
          "title": "Is Abstract",
          "type": "Checkbox",
          "options": []
        },
        {
          "name": "comment",
          "title": "Comment",
          "type": "Select",
          "options": ["Option A", "Option B", "Option C"]
        },
        {
          "name": "richText",
          "title": "Rich Text with Completion",
          "type": "RichText",
          "options": ["Completion A", "Completion B", "Completion C"]
        },
        {
          "name": "collection",
          "title": "Collection of text fields",
          "type": "Collection",
          "options": []
        }
      ]
    }
  ]
}
```
</details>

And in case of your autocomplete script will return template like above, you will have the following menu option to
generate a new file from template on any path in project structure (path may be filtered by `path_regex` option):
<details>
  <summary>Expand</summary>

![file_template_example](doc/images/file_template_menu_example.png)
</details>

When You click on the action, you'll see the following form:
<details>
  <summary>Expand</summary>

![file_template_example](doc/images/file_template_preview_example.png)
</details>

On this form you can modify any of your variables described above. Preview is updated automatically after you change
the value of any variable.

After clicking `OK` button, the file will be generated in the folder where you initialed the action.

After opening the form, after changing any of the variable and on clicking OK, plugin will send a request for code
generation to your autocomplete script with the following variables:

- `PSA_TYPE` - will be always `GenerateFileFromTemplate`
- `PSA_CONTEXT` - like with completion, it's a path to file with JSON of following structure:
  ```JSON
  {
    "templateName": "string, name of the template for generate.",
    "actionPath": "string, relative path from project root when the action were initiated.",
    "formFields": {
      "name": "value"
    },
    "originatorFieldName": "string, optional. If template regeneration were cause by some field change, this option will contain this field name."
  }
  ```
> [!NOTE]
> `formFields` - will be a JSON object where each key is a field name, and value will be a value of the form field.

As a result, your script should return a simple JSON object with the following fields:
```JSON
{
  "file_name": "string, required. Filename of the newly generated file.",
  "content": "string, required. Content of the file.",
  "form_fields": {
    "{field_name}": {
      "options": "Array of strings, optional. Here you can override array of `RichText` completions.",
      "value": "String, optional. Here you can override current value of any form field if needed."
    }
  }
}
```
> [!NOTE]
> `form_fields` - is an optional field. Each inner value of `form_fields` is optional as well.

Some examples for [PHP](examples/php/psa.php), [JavaScript](examples/js/psa.js), [TypeScript](examples/ts/psa.ts) 
are shown in the [examples](examples/README.md) folder.


#### Multiple File Template

Creation of multiple file template is looking same as single file template, but in this case you need to return an
additional field: `file_count` in template config, which will show the count of files you're want to create, as well as
generation of file template is returning array of file paths and array of file contents.

In case of you need to create a multiple file template, in info request your JSON should contain template with the
following fields:
- `type` - string, required. `single_file` or `multiple_file` are supported. For multiple file template pass `multiple_file`
  as a value.
- `name` - string, required. Name of the template for reference. Will be passed in `PSA_CONTEXT` during template generation.
- `title` - string, required. Title of the template. This text will be shown in IDE.
- `path_regex` - string, optional. Regular expression to filter paths where template creation action will be available.
- `fields` - array of objects with the following structure:
  - `name` - string, required. Name of the form field. Will be passed in `PSA_CONTEXT` during template generation.
  - `title` - string, required. Title of the field which will be displayed in form.
  - `type` - string, required. Allowed values are `Text`, `Checkbox`, `Select`, `Collection`, `RichText`. Type of the form field.
  - `focused` - boolean, optional. Set to true for the field you want to be focused on opening template creation dialog.
  - `options` - array of strings.
    - Required if `type` is `Select`. Array of select options.
    - Required if `type` is `RichText`. Array of completions.
- `file_count` - integer, required. Count of files in multiple file template

For example, you can use the following structure:
<details>
  <summary>Expand</summary>

```JSON
{
  "templates": [
    {
      "type": "multiple_file",
      "name": "my_awesome_multi_file_template",
      "title": "My Awesome Multi-file template",
      "path_regex": "^\/src\/[^\/]\/$",
      "fields": [
        {
          "name": "someName",
          "title": "Some Name",
          "type": "Text",
          "options": []
        }
      ],
      "file_count": 3
    }
  ]
}
```
</details>

And in case of your autocomplete script will return template like above, you will have the following menu option to
generate a new file from template on any path in project structure (path may be filtered by `path_regex` option):
<details>
  <summary>Expand</summary>

![file_template_example](doc/images/file_template_menu_example.png)
</details>

When You click on the action, you'll see the following form:
<details>
  <summary>Expand</summary>

![multi_file_template_example](doc/images/multi_file_template_preview_example.png)
</details>

On this form you can change tabs, which are representing each file you're providing, modify any of your variables 
described above. Preview is updated automatically in all files after you change the value of any variable.

After clicking `OK` button, the files will be generated in the paths returned by your template.

After opening the form, after changing any of the variable and on clicking OK, plugin will send a request for code
generation to your autocomplete script with the following variables:

- `PSA_TYPE` - will be always `GenerateFileFromTemplate`
- `PSA_CONTEXT` - like with completion, it's a path to file with JSON of following structure:
  ```JSON
  {
    "templateName": "string, name of the template for generate.",
    "actionPath": "string, relative path from project root when the action were initiated.",
    "formFields": {
      "name": "value"
    },
    "originatorFieldName": "string, optional. If template regeneration were cause by some field change, this option will contain this field name."
  }
  ```
> [!NOTE]
> `formFields` - will be a JSON object where each key is a field name, and value will be a value of the form field.

As a result, your script should return a simple JSON object with the following fields:
```JSON
{
  "file_names": "array of strings, required. Filenames of the newly generated files (full path from project root).",
  "contents": "array of strings, required. Contents of the files.",
  "form_fields": {
    "{field_name}": {
      "options": "Array of strings, optional. Here you can override array of `RichText` completions.",
      "value": "String, optional. Here you can override current value of any form field if needed."
    }
  }
}
```
> [!NOTE]
> `form_fields` - is an optional field. Each inner value of `form_fields` is optional as well.

### Editor Actions

Sometimes you don't need to generate a whole file from template, but rather - process some part of code and replace it
right in the editor. For these purposes one made Editor Actions.

To support editor actions, your `Info` response should contain `editor_actions` field. It should be an array with the
following structure:

<details>
  <summary>Expand</summary>

```JSON
{
  "editor_actions": [
    {
      "name": "string, required. Name of the action, will be passed back to your script on calling",
      "title": "string, required. Title of the action to show within IDE actions",
      "source": "string, required. May be either 'editor' or 'clipboard'. The source of data. Either selected text or clipboard",
      "target": "string, required. May be either 'editor' or 'clipboard'. The target of data. Either replace selected text or copy result to clipboard",
      "group_name": "string, optional. Adds a sub-menu in PSA Actions menu",
      "path_regex": "string, optional. PAth regular expression to filter where this action will be shown"
    }
  ]
}
```
</details>

For example, you can create an action that will convert JSON into PHP array:

<details>
  <summary>Expand</summary>

```php
<?php

$type = getenv('PSA_TYPE');

if ('Info' === $type) {
    echo json_encode([
        'supported_languages' => ['PHP'],
        'editor_actions' => [
            [
                'name' => 'jsonToPhpArray',
                'title' => 'Convert JSON -> PHP array (Copy to Clipboard)',
                'source' => 'editor',
                'target' => 'clipboard',
                'group_name' => 'JSON',
            ],
        ],
    ]);

    exit(0);
}

$context = json_decode(file_get_contents(getenv('PSA_CONTEXT')), true);

if ('PerformEditorAction' === $type && $context['action_name'] === 'jsonToPhpArray') {
    $data = json_decode($context['text'], true);

    echo var_export($data);
}
```
</details>

And in case of your PSA script will return the value and then you can paste it to any place of your code.
For example, it's useful with `Generate Pattern Model` action, so. You run this action, copy result into some tmp JSON
file, remove everything not need, then run your own action to convert it into PHP array and Voilà:
![convert_json_to_php_array](doc/images/convert_json_to_php_array.png)
and then somewhere in your PSA script code (before paste):
![convert_json_to_php_array_paste_before](doc/images/convert_json_to_php_array_paste_before.png)
and after paste:
![convert_json_to_php_array_paste_after](doc/images/convert_json_to_php_array_paste_after.png)


### Performance considerations

#### General

Calling external program/API is not fast by nature. Things are making worse in case of
program is need to do some long computations (like compiling TypeScript, or building some cache (like Symfony does)).

So, keep it in mind and make some long computations as the last part of your code. For example, if
you need to autocomplete Symfony services, boot Symfony kernel only in case of you sure that
completions may be applied in the current context.
<details>
  <summary>Expand</summary>

Do:
```php
<?php

$context = json_decode(file_get_contents(getenv('PSA_CONTEXT')), true);

if (!checkElement($context)) {
  echo json_encode(['completions' => [], 'notifications' => []]);

  die;
}

$kernel = bootKernel();
```
instead of:
```php
<?php

$context = json_decode(file_get_contents(getenv('PSA_CONTEXT')), true);
$kernel = bootKernel();

if (!checkElement($context)) {
  echo json_encode(['completions' => [], 'notifications' => []]);

  die;
}
```
</details>

It's always better to use static completions instead of dynamic completions, such as they are working much faster and
less resource consuming.

#### GoTo optimizations

When you're clicking (Ctrl/Command + Click) by **any** element in the IDE editor, IDE is calling all GoTo
contributors, regardless of language or any other things. And there is no way to know - does your custom autocomplete
will resolve the reference or not. So plugin will still try to call your script to check that element may be resolved, 
and GoTo reference provided. This will lead to freezes in IDE UI when you're trying to GoTo in some place which 
your custom autocomplete is not support. To overcome this problem, there is an additional option were added:
`goto_element_filter`. Here your script should return an array of element types to filter GoTo references. It will be 
saved first time your script will be called with [Info](#custom-autocomplete-info) call and then will ignore all 
elements that are not matching the types provided.

### StatusBar Icon
Plugin provides a status bar icon which is showing current status of autocomplete. Icon is showing only in case of 
plugin is enabled in settings. Also, if you want, you can hide it by right-click on the status bar.
Icon is showing either green ![active_image](src/main/resources/icons/pluginIcon_active_16.svg) or 
red ![active_image](src/main/resources/icons/pluginIcon_error_16.svg) dot on the left top corner, showing the result of
last PSA operation. If result wee succeed, icon will be green and red otherwise.

Also, if you click on the icon, a quick plugin actions menu will be shown:

![widget_menu](doc/images/psa_widget_popup_menu.png)

where you can see plugin actions menu.

## Ideas / ToDo

- [x] Add support of autocomplete
- [x] Add support of GoTo
- [x] Add support of single-file custom code templates with variables
- [x] Add support of multi-file custom code templates with variables
- [ ] Add support of intentions

## FAQ / How To

**Q: What if I run my project in Docker?**

**A:** It's no problem. You can easily use it with the plugin. See examples for 
[docker-compose](examples/docker-compose) or [docker](examples/docker).

**Q: What about some API projects, like Nest/Next, which are starting slow?**

**A:** Yeah, it's problem for Nest/Next to compile each time you're trying to autocomplete the code. But there is a 
solution: you can implement some route, which will be accessible in DEV environment only, and respond with completions.
See [example](examples/api).

**Q: What if I want to use some feature that is not yet supported?**

**A:** Please, create an [issue](https://github.com/sam0delkin/intellij-psa/issues/new) for that. Describe the problem 
very thoroughly.

**Q: What if I want to implement some feature that is not yet supported?**

**A:** That's really great 😊. Please, [fork](https://github.com/sam0delkin/intellij-psa/fork) the repository and then
create a [pull request](https://github.com/sam0delkin/intellij-psa/compare).

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
