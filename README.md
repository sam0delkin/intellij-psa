# intellij-psa
## Intellij Project-Specific Autocomplete

![Build](https://github.com/sam0delkin/intellij-psa/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/PLUGIN_ID.svg)](https://plugins.jetbrains.com/plugin/PLUGIN_ID)

<!-- Plugin description -->
Small plugin which adds support for custom autocomplete & GoTo on the language you're writing your project.

![example](https://github.com/sam0delkin/intellij-psa/raw/main/src/main/resources/doc/images/autocomplete_example.png)

For check how to add custom autocomplete, please read [documentation](https://github.com/sam0delkin/intellij-psa#documentation) 
<!-- Plugin description end -->

## Installation

[//]: # (- Using IDE built-in plugin system:)

[//]: # (  )
[//]: # (  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "intellij-psa"</kbd> >)

[//]: # (  <kbd>Install Plugin</kbd>)

[//]: # (  )
- Manually:

  Download the [latest release](https://github.com/sam0delkin/intellij-psa/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## Supported Languages
* PHP
* JavaScript
* others - todo


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
Plugin is passing the following data as ENV variables into executable:
* `PSA_CONTEXT` - file path that contain the JSON-encoded PSI context.
* `PSA_TYPE` - may be either `Completion` or `GoTo`. Type of the execution.
* `PSA_LANGUAGE` - language which is caused the autocomplete/resolving reference (`PHP`, `JS`, ...).
* `PSA_DEBUG` - `1` in case of debug is enabled in plugin settings and 0 otherwise.

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
> In the output above the `options` option is omitted to make output less size.

## Documentation

As it already mentioned in [introduction](#how-it-works), plugin is sending JSON-encoded PSI tree into the executable.
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
  "next": "additional tree element"
}
```
</details>

By analyzing element and it's parents + some options you may find how to check that the caret is on the element
which may be autocompleted. As a result your script should return:

* Array of completions in case of `PSA_TYPE` is `Completion`.
* Array of completions with one element (this element should contain a link) in case of `PSA_TYPE` is `GoTo`.
* Optional, you can pass notifications array which will be show by IDEA. Useful for debug purposes.

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
      "type": "string, the type which will be shown as grayed text on the right of completion."
    }
  ],
  "notifications": [
    {
      "type": "string, may be either 'error' or 'warning'.",
      "text": "string, the text of the notification."
    }
  ]
}
```
</details>

> [!NOTE]
> In case of action is GoTo, you should return only one completion with the link to reference.
