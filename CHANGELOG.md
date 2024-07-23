<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# intellij-psa Changelog

## [Unreleased]
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

[Unreleased]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.12...HEAD
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
