<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# SlimEnum Changelog

## [Unreleased]

## [0.0.12] - 2026-04-22

### Changed

- Remove the `until-build` upper bound from the plugin descriptor. SlimEnum
  uses only stable Java PSI and `CompletionContributor` APIs, so the same
  artifact works on every current and future IntelliJ build (2025.2, 2025.3,
  2026.1, and onward) without chasing IDE release numbers.

[Unreleased]: https://github.com/cheblin/SlimEnum/compare/0.0.12...HEAD
[0.0.12]: https://github.com/cheblin/SlimEnum/commits/0.0.12
