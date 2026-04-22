<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# xxx Changelog

## [Unreleased]

## [0.0.11] - 2026-04-22

### ### Added

- **SlimStruct** — new opaque-type support for bit-packed records. Declare a
    struct as an `@interface` whose nested `@interface`s expose a `Val` inner
    interface with `static` `get`/`set` (and optional `hasValue`/`to_null`) for
    each field. Dot-access on a pack-typed variable lists every field's accessors
    in the completion popup; selecting one rewrites `pack.x` to
    `Struct.field.Val.x(pack, …)` with imports auto-collapsed.
- **Annotation propagation across assignments, casts, returns, and ternaries.**
    Declaring `short s = perm;` still shows `FilePerm` accessors on `s`.
    Propagation walks the enclosing method for local variables and the file for
    fields.
- **Writer statement-wrapping.** Typing `addr.` on a standalone line and
    picking a writer (`set`, `to_null`, …) rewrites the whole statement as
    `addr = Struct.field.Val.set(<caret>, addr);` — both the assignment back to
    the variable and the closing semicolon are inserted for you.
- **`@<Struct>.Nullable` wrappers** get a distinct completion set that steers
    you through `hasValue` → `get` before you can unwrap the pack.
- New plugin icon (slim-figure silhouette with dedicated light/dark variants).

### ### Changed

- SlimEnum now inserts the fully-qualified constant name
    (`Font.Foreground.BLACK`) and lets `shortenClassReferences` collapse the
    prefix against the file's imports — no more bare `BLACK` tokens that fail to
    resolve.
- SlimEnum now recognizes `@Target(TYPE_USE)` annotations applied to the type
    rather than the declaration, so parameters generated with `@Font byte style`
    (with `Font` targeting only `TYPE_USE`) are matched correctly.
- SlimEnum covers more contexts: array-element assignment
    (`array[i] = <caret>` where `array` is annotated), return statements, RHS of
    `==`/`!=` comparisons, `switch` scrutinees and case labels.
- Default Java completion is suppressed only when the plugin actually
    contributes items; everywhere else IntelliJ's built-in completion runs
    untouched.
- Build targets IntelliJ Platform **2025.2.6.1** on Java **21**, Gradle
    **9.4.1**, Kotlin **2.1.20**. Minimum supported IDE build raised to **252**.
- GitHub Actions workflows modernized (actions v5/v6, `setup-gradle`,
    concurrency guards, separate test/verify/build jobs, draft-release on push).

### ### Fixed

- Flag exclusion in polyadic expressions now fires for the common
    `rhs = A | B | <caret>` shape; previously the check required a PSI layout
    that rarely occurred.
- `@Font byte x = Font.BOLD | <caret>` no longer mis-identifies the sibling
    operand as the annotation owner — the correct flag constants are offered.
- Cast expressions (`(byte)(Font.BOLD | <caret>)`) and parenthesised
    expressions no longer interrupt the completion context walk.

### ### Removed

- Qodana configuration and workflow steps.
- `libs.versions.toml`; plugin versions are inlined in the build scripts.
- Legacy `xyz.unirail` package (moved to `org.unirail` to match group id).

  ---

[Unreleased]: https://github.com/cheblin/SlimEnum/compare/0.0.11...HEAD
[0.0.11]: https://github.com/cheblin/SlimEnum/commits/0.0.11
