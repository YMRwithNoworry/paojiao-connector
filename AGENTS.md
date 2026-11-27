# Repository Guidelines

## Project Structure & Module Organization

The root Gradle project contains the NeoForge mod runtime in `src/main/java` and its resources in `src/main/resources`. Root tests live in `src/test/java`. The `loader` subproject discovers and transforms legacy mod jars, with its own `src/main` and `src/test` trees. `testmods` contains fixture mods used by compatibility tests. Reference material and support files are in `docs`, `examples`, `assets`, `tools`, and `.github/workflows`. `settings.gradle` defines the included Gradle subprojects.

## Build, Test, and Development Commands

Use Java 21, matching the CI workflow. From the repository root in PowerShell, run:

- `.\gradlew.bat build --no-daemon` to build and run the configured test suite, as CI does.
- `.\gradlew.bat test --no-daemon` to run root-project tests.
- `.\gradlew.bat :loader:test --no-daemon` to run loader tests.

## Coding Style & Naming Conventions

Follow the surrounding Java and Gradle style, using four-space indentation. Keep Java package names lowercase, class names in `PascalCase`, and methods and fields in `camelCase`. Keep tests named after the behavior or class under test and end test classes with `Test`. Avoid unrelated formatting changes in compatibility tables and generated resources.

## Testing Guidelines

Add focused regression tests in the relevant module's `src/test/java` tree. Use existing JUnit 5 conventions and fixture mods for legacy-loading scenarios. Run the narrow module test while iterating, then run `build` before submitting changes. Include the legacy Minecraft/Forge version in test names or descriptions when it clarifies the compatibility target.

## Commit & Pull Request Guidelines

Recent commits generally use short, imperative subjects, with `docs:` used for documentation updates. Keep commits focused. Pull requests should describe the compatibility behavior changed, link related issues, and report the Gradle tasks run. Include logs, screenshots, or reproducible steps for startup, rendering, or in-game changes.

## Agent Workflow

Run repository commands in PowerShell (`pwsh`). After completing a task, push the completed changes to GitHub. If the repository has no GitHub remote, create one before pushing.
