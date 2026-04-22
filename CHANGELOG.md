<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# SlimEnum Changelog

## [Unreleased]

### Changed

- Remove the `until-build` upper bound from the plugin descriptor. SlimEnum
  uses only stable Java PSI and `CompletionContributor` APIs, so the same
  artifact works on every current and future IntelliJ build (2025.2, 2025.3,
  2026.1, and onward) without chasing IDE release numbers.
