# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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
- Database: `database/` (driver, migrations)
- Tests: Mirror main structure in `src/test/`

## Database Migration Guidelines

### Creating a New Migration
When adding new database migrations:

1. **File Location**: Place new migrations in `src/main/kotlin/li/angu/challengeplugin/database/migrations/`
2. **Naming Convention**: Use format `Migration{XXX}{DescriptiveAction}.kt` where XXX is zero-padded version number
3. **Examples**: Check existing migrations (`Migration001CreateInitialTables.kt`, `Migration002AddChallengeSettings.kt`, `Migration003AddPlayerData.kt`) to understand the structure and patterns
4. **Registration**: Add your new migration to the list in `MigrationManager.kt`
5. **Version Numbers**: Always increment version number sequentially - never modify existing migrations
6. **Best Practices**: Use `IF NOT EXISTS` for CREATE statements, batch multiple statements with `executeBatch()`, and test thoroughly

### Database Query Guidelines

**NEVER use `INSERT OR REPLACE`** - This SQL command deletes and recreates records, which:
- Breaks foreign key relationships and cascades
- Can cause data loss in related tables
- Triggers unnecessary constraint violations

**Instead use proper INSERT/UPDATE logic**:
- Check if record exists first with a SELECT query
- Use INSERT for new records, UPDATE for existing records
- Maintain referential integrity and avoid data loss

Example pattern:
```kotlin
val exists = checkIfRecordExists(id)
val query = if (exists) {
    "UPDATE table SET ... WHERE id = ?"
} else {
    "INSERT INTO table (...) VALUES (...)"
}
```