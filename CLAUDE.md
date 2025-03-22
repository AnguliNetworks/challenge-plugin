# ChallengePlugin Development Guide

## Build & Test Commands
- Build: `./gradlew build`
- Run tests: `./gradlew test`
- Single test: `./gradlew test --tests "li.angu.challengeplugin.utils.TimeFormatterTest"`
- Clean build: `./gradlew clean build`
- Build JAR: `./gradlew jar`

## Development Workflow
- Do not run build commands automatically after changes - let the user run them when ready to test
- The user will handle testing in their Minecraft environment

## Code Style Guidelines
- **Kotlin Style**: Follow standard Kotlin style conventions
- **Indentation**: 4 spaces (no tabs)
- **Line Length**: Keep under 120 characters
- **Naming**:
  - Classes: PascalCase (e.g., `ChallengeManager`)
  - Functions/Variables: camelCase (e.g., `getPlayerChallenge()`)
  - Constants: UPPER_SNAKE_CASE
- **Imports**: No wildcard imports, organize by package
- **Error Handling**: Use early returns with guard clauses
- **Nullability**: Prefer non-nullable types, use `?` only when necessary
- **Documentation**: Document public APIs and complex logic
- **Language**: Support English and German translations in language files

## Project Structure
- Plugin core: `ChallengePluginPlugin.kt`
- Models: `models/` (e.g., `Challenge.kt`)
- Managers: `managers/` (e.g., `ChallengeManager.kt`)
- Utilities: `utils/` (e.g., `TimeFormatter.kt`, `LanguageManager.kt`)
- Commands: `commands/` (e.g., `ChallengeCommand.kt`)
- Tests: Mirror main structure in `src/test/`