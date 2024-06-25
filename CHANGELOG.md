<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# intellij-psa Changelog

## [Unreleased]
- Added file watcher so any change in a PSA script dir will automatically update info

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

[Unreleased]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.8...HEAD
[0.0.8]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.7...v0.0.8
[0.0.7]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.6...v0.0.7
[0.0.6]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.5...v0.0.6
[0.0.5]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.4...v0.0.5
[0.0.4]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.3...v0.0.4
[0.0.3]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/sam0delkin/intellij-psa/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/sam0delkin/intellij-psa/commits/v0.0.1
