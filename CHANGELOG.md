<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# intellij-psa Changelog

## [Unreleased]
- Removed some internal API usages
- Some small refactorings

## [0.0.22] - 2025-03-29
- Passing a relative file path (from project root) to the `PerformEditorAction` action instead of just file name
- Implemented a smart references to static GoTo. So, even if file is changed, reference to the element will be still
valid
- Now you can use a `link` like `FilePath:line:column` to match elements
- Resolving references. Now you can go back from the GoTo to the original element. Works only in context of static
completions.
- Added an optional weak warning (Intention) in case of element is not matching any completions. Works only in context 
of static completions.
- Refactor classes/files structure
- Improved documentation
- Improved performance
- Fixed some minor issues

## [0.0.21] - 2025-03-19
- Fixed a problem with Editor Action Groups
- Refactoring of Swagger docs, so main project is now not including swagger classes, plugin size decreased
- Added a settings option to show errors even if debug is disabled
- Added an optional property `matcher` to the static completions, which is using Apache Velocity to match elements
- Added support for better element matching, now you can use a `link` like `FilePath:line:column` to match elements
- Starting to work on reference contributor. Not available now, but will be added in the future releases
- Improved documentation
- Fixed some minor issues

## [0.0.20] - 2025-03-13
- Remove some Intellij Internal API usage
- Added support of `any_parent`, `any_next`, `any_prev` to `PsiElementPatternModel` for easier pattern matching
- Fixed some bugs when static completions are not automatically updated

## [0.0.19] - 2025-03-13
- Refactorings of static completions
- Introduced editor actions. For more details, see https://github.com/sam0delkin/intellij-psa/tree/main?tab=readme-ov-file#editor-actions
- Minor bug fixes and performance improvements

## [0.0.18] - 2025-03-09
- Remove support of indexing, such as it's not fit the plugin needs due to some restrictions of chained method calls.
Also, it's no longer needed due to a new feature - static completions.
- Static Completions introduced. Now you can create some element patters and use them for way faster completions and
goto.
- Refactorings of models
- Fix swagger doc coding style (used camelCase elements instead of under_score).
- Minor typo and bug fixes

## [0.0.17] - 2025-03-08
- Major refactoring. Move to JSON Models instead of calling methods
- Add Swagger doc for easier understanding + DTO generation
- Bug fixes and performance improvements

## [0.0.16] - 2025-01-25
- Removed some internal/deprecated API usage to be compatible with 2025.1
- Significantly improved indexing performance as well as index using process. For now
indexing will not re-index the whole file in case of file modifications.
- Small typo fixes in README.md

## [0.0.15] - 2024-11-10
- Added optional `presentable_text` and `tail_text` options for completions.
- Added `focused` option to template form fields. Allows to focus on the field when the template is opened.
- Added multiple file template support
- Fixed a bug which causes error in case of indexing is not enabled

## [0.0.14] - 2024-08-10
- Fix bug that completions not working if plugin enabled but not used
- Added ktlint
- In case of multiple file changes and previous indexing is still running, it will now be cancelled
- Fixed bug with code templates keep showing in case of new Info returned no templates
- Added `Indexing Batch Count` settings option
- Added `Indexing Max File Elements` settings option
- Added `Process only indexed elements` settings option
- Various bug and performance fixes

## [0.0.13] - 2024-07-23
- Fixed bug that cause autocomplete not working when plugin is disabled

## [0.0.12] - 2024-07-22
- Make indexing optional. Can be disabled in settings
- If file is successfully indexed, PSA will not try to do any completions without using index
- Indexing file progress is now more informative
- Performance improvements in indexing process

## [0.0.11] - 2024-07-20
- Added support of completion/GoTo indexing. Now all completions and GoTo are running instantly, without any delays, 
right after currently opened file will be indexed
- Added support of `BatchGoTo` and `BatchCompletion` operations to speed-up processing during indexing
- Added support of returning value for form fields in single file template generation. This way you can pre-populate
form field with a needed value
- Added `originatorFieldName` into the context of single file generation. Will contain a field name which value change
caused regeneration of the template content
- Added info icon on the settings to get all the languages supported by your IDE. Useful in case of you don't know how
to exactly need to specify the language in `supported_languages` option.
- A lot of bug fixes and performance improvements

## [0.0.10] - 2024-06-26
- Added StatusBar icon
- Added option to update completions for `RichText` field type during template generation
- PSA now will update status after project start
- PSA will ask to update plugin if it is not yet enabled, and executable script found and `.psa/psa.sh`
- Documentation and examples update
- Misc fixes and improvements

## [0.0.9] - 2024-06-26
- Added file watcher so any change in a PSA script dir will automatically update info
- Added `RichText` file template field type to support completions. You can provide array of completions in `options` 
field
- Misc improvements

## [0.0.8] - 2024-06-24
- Fixed freezes during debug enabled
- Added `Collection` file template field type
- Added `Timout` option to the settings form
- Improved widget icon menu

## [0.0.7] - 2024-06-23
- Added support of custom `single_file` file templates
- Added toolbar icon with current autocomplete status + actions for update info and check last error.
- Fixed all "slow/blocking operations in EDT/ReadAction" issues by using threading
- Lot of additional refactorings
- Small typo fixes

## [0.0.6] - 2024-06-22
- Added `textRange` to the `PSA_CONTEXT` ENV variable. Shows PSI element position is the file
- Added `PSA_OFFSET` ENV variable. Shows cursor position within current element in editor
- Moved settings form to Kotlin UI DSL + improved it a lot
- Changed IDE minimum version to 2022.1 to support old IDEs

## [0.0.5] - 2024-06-18
- Performance optimizations
- Prev/Next are now in correct places :)
- Prev/Next tree is generated fully
- Misc code clearings

## [0.0.4] - 2024-06-16
- Settings structure is changed. You should reconfigure plugin in setting
- Added support of all programming languages
- Fixed platformType (change back to IU)
- Updated & improved documentation
- Automatically publish plugin to JetBrains Marketplace
- Misc fixes

## [0.0.3] - 2024-06-12
- Added support for TypeScript language
- Added support for `priority` in completions
- Added support for automatic GoTo element filters by `goto_element_filter` option and removed this options from settings
- Added support for `info` notifications
- Updated README.md based on JetBrains marketplace response
- Updated documentation
- Added examples
- Added LICENSE
- Misc fixes & improvements

## [0.0.2] - 2024-06-09
- Performance improvements
- Added some initial documentation
- Misc fixes

## [0.0.1] - 2024-06-09
Initial Release

[Unreleased]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.22...HEAD
[0.0.22]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.21...v0.0.22
[0.0.21]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.20...v0.0.21
[0.0.20]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.19...v0.0.20
[0.0.19]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.18...v0.0.19
[0.0.18]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.17...v0.0.18
[0.0.17]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.16...v0.0.17
[0.0.16]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.15...v0.0.16
[0.0.15]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.14...v0.0.15
[0.0.14]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.13...v0.0.14
[0.0.13]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.12...v0.0.13
[0.0.12]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.11...v0.0.12
[0.0.11]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.10...v0.0.11
[0.0.10]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.9...v0.0.10
[0.0.9]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.8...v0.0.9
[0.0.8]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.7...v0.0.8
[0.0.7]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.6...v0.0.7
[0.0.6]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.5...v0.0.6
[0.0.5]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.4...v0.0.5
[0.0.4]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.3...v0.0.4
[0.0.3]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/sam0delkin/intellij-psa/commits/v0.0.1
