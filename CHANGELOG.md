<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# SlimEnum Changelog

## [Unreleased]

### Added

- Value packs (SlimStruct) in Java: completion for the accessors gathered in
  groups, `Pack.get.field( pack )`, `pack = Pack.set.field( pack, value )`,
  `Pack.hasValue.field( pack )`, `pack = Pack.to_null.field( pack )`,
  `Pack.New.of( … )`. The name of a field alone finds its accessors.
- A place that takes a pack offers what makes one: `Pack.New.of( … )` and the
  constants of the pack, such as `EMPTY_PACK`.
- Value packs in TypeScript: the same groups for a pack that is a `number`,
  a `bigint` or an object of a class. Needs the *JavaScript and TypeScript*
  plugin; without it the Java side works alone.

### Changed

- Requires IntelliJ IDEA 2026.2 or later.
- The layout with a nested `@interface` for every field,
  `Pack.field.Val.get( pack )`, is still understood.
