---
description: 'SignWarpX Java development conventions'
applyTo: '**/*.java'
---

# SignWarpX Java Conventions

This project is a Minecraft Paper plugin. Stack: **Java 21 (Temurin)** / **Paper API 1.21.8-R0.1**, built with Gradle + Shadow + ProGuard.

## Formatting

- 4-space indent, K&R braces, ~130 char line width (SQL/MiniMessage URLs may exceed), UTF-8
- One blank line between methods; blank lines to separate logical sections within methods
- **No wildcard imports**
- Import order: `com.swim.*` → third-party → `org.*` → `java.*`
- When two classes share the same simple name, keep the less-used one as a FQN inline

## Naming

| Kind | Convention | Example |
|------|-----------|---------|
| Class | `PascalCase` | `WarpCacheManager` |
| Method / Field | `camelCase` | `getAccessibleWarps` |
| Constant | `UPPER_SNAKE_CASE` | `MAX_TELEPORT_COUNT` |
| Package | lowercase | `com.swim.signwarpx.data.repository` |
| Suffixes | `Handler` / `Manager` / `Utils` | `SignEventHandler`, `SafetyUtils` |

- Use nouns for classes, verbs for methods. Avoid abbreviations and Hungarian notation.
- **No `var`** — always use explicit types.

## Types & Annotations

- `@NotNull` / `@Nullable` from `org.jetbrains.annotations`; `@NonNull` from `org.jspecify` only for array elements.
- Lombok `@Getter` / `@Setter` / `@Data` — ignore LSP false errors from generated methods.
- Fields must be `private` (or `private final`); utility class constructors must be `private`.
- Use `final` for fields and constants; not required for local variables.
- Declare variables close to first use.

## Best Practices

- **Records** — Use Java records for immutable data classes (DTOs, value objects) instead of traditional classes.
- **Pattern Matching** — Use pattern matching for `instanceof` and switch expressions to simplify type checks and casts.
- **Immutability** — Favor immutable objects. Fixed data: `List.of()` / `Map.of()`. Stream results: `Stream.toList()`.
- **Streams & Lambdas** — Use the Streams API for collection processing; prefer method references (`Foo::toBar`).
- **Null Handling** — Avoid returning or accepting `null`. Use `Optional<T>` for absent values; use `Objects.equals()` / `Objects.requireNonNull()`.

## Switch

- Prefer **arrow-style switch expressions** with `yield`.
- Traditional `switch` + `break` is acceptable for simple tab-completion cases.

## Collections

- **Hot paths** → FastUtil: `ObjectArrayList`, `Object2ObjectOpenHashMap`, `ObjectOpenHashSet`, etc.
- **Cross-thread shared state** → `ConcurrentHashMap` only.
- **Main-thread-confined logic** → `HashMap` / `EnumMap`.
- **Config / low-frequency paths** → standard Java: `EnumSet`, `Map.of()`.

## Messages & Logging

- Player messages come from `messages.yml`, parsed via `MessageUtils.parseMessage()` (MiniMessage / MineDown / `\&` codes).
- Use `java.util.logging.Logger`:
  - `severe` — errors
  - `warning` — recoverable issues
  - `info` — lifecycle events
  - `fine` — debug
- Always include meaningful context in log messages (warp name, player name, SQL error).

## Class Internal Order

1. Static constants → 2. Instance fields → 3. Constructor(s) → 4. Public methods → 5. Private helpers

- Use `// ========== Section ==========` banners in large classes.
- Javadoc with `<p>` / `<ul>` / `<li>`; document `@param` / `@return` for all public API methods.

## Common Bug Patterns

- **Resource management** — Always close resources (files, sockets, streams). Use try-with-resources.
- **Equality checks** — Use `.equals()` or `Objects.equals(...)` for non-primitives; never `==`.
- **Redundant casts** — Remove unnecessary casts; prefer correct generic typing.
- **Reachable conditions** — Avoid conditions that are always true or false; they indicate bugs or dead code.

## Common Code Smells

- **Parameter count** — Keep parameter lists short; group into a value object or use Builder if too many.
- **Method size** — Keep methods small and focused; extract helpers to improve readability and testability.
- **Cognitive complexity** — Reduce nesting and branching by extracting methods, using polymorphism, or applying Strategy.
- **Duplicated literals** — Extract repeated strings and numbers into named constants or enums.
- **Dead code** — Remove unused variables and assignments.
- **Magic numbers** — Replace numeric literals with named constants (e.g., `MAX_RETRIES`).

## Build & Verification

```bash
./gradlew build -PskipObfuscation          # dev build (skip obfuscation)
./gradlew test                              # run all tests
./gradlew clean build -PskipObfuscation    # clean rebuild
```

After any code change, confirm the project builds successfully and all tests pass.
