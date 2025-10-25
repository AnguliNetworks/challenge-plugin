# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# ChallengePlugin Development Guide

## Build & Test Commands
- Build: `./gradlew build`
- Run tests: `./gradlew test`
- Single test: `./gradlew test --tests "li.angu.challengeplugin.utils.TimeFormatterTest"`
- Clean build: `./gradlew clean build`
- Build JAR: `./gradlew jar`
- Deploy to test server: `./gradlew deployToTestServer -x test`

## Development Workflow
- After completing features or fixes, use `./gradlew deployToTestServer -x test` to deploy directly to test server for in-game testing
- The deployToTestServer task creates a consistently-named JAR (`ChallengePlugin-dev.jar`) in `run/plugins/` for easy server testing
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

## Project Architecture

### Core Plugin Structure
- **Main Plugin Class**: `ChallengePluginPlugin.kt` - Entry point, initializes all managers and registers commands/listeners
- **Initialization Order** (critical):
  1. Database (must be first - plugin disables if this fails)
  2. LanguageManager
  3. PlayerDataManager
  4. ChallengeManager (loads saved challenges from database)
  5. UI Managers (SettingsInventoryManager, ChallengeMenuManager)
  6. WorldPreparationManager
  7. LobbyManager, ElytraManager
  8. Commands and Listeners

### Manager System
Managers handle distinct domains of functionality:
- **ChallengeManager**: Core challenge lifecycle (create, join, leave, complete), world loading/unloading, player-challenge mapping
- **PlayerDataManager**: Save/restore player state (inventory, location, game mode, health, etc.) per challenge
- **SettingsInventoryManager**: GUI inventory for challenge settings configuration
- **ChallengeMenuManager**: GUI menu for browsing/selecting challenges
- **LobbyManager**: Lobby location management and teleportation
- **WorldPreparationManager**: Pre-generation of challenge worlds with Chunky integration
- **ElytraManager**: Special elytra mechanics for challenges

### Command Architecture
- **BaseCommand**: Abstract base class providing common command execution and tab completion structure
- All commands extend `BaseCommand` and implement `execute()` and optionally `tabComplete()`
- Commands are registered in `ChallengePluginPlugin.onEnable()` using `registerCommand()` helper
- Main command hub is `/challenge` with subcommands routed through `ChallengeCommand.kt`

### Data Model
- **Challenge**: Main model in `models/Challenge.kt`
  - Tracks players (UUIDs), status (ACTIVE/COMPLETED/FAILED), timing (with pause support)
  - Contains `ChallengeSettings` (natural regeneration, sync hearts, block randomizer, starter kits, level-based world border)
  - Each challenge has 3 worlds: overworld, nether (`{worldName}_nether`), end (`{worldName}_the_end`)
  - Timer automatically pauses when empty, resumes when players join
- **ChallengeSettings**: Challenge behavior configuration
- **StarterKit**: Enum defining starting equipment tiers (NONE, BASIC, STONE, IRON, DIAMOND)

### Database Architecture
- SQLite database with migration system
- **MigrationManager** runs migrations sequentially on startup
- Migrations are in `database/migrations/` with naming: `Migration{XXX}{DescriptiveAction}.kt`
- Key tables:
  - `challenges`: Main challenge data
  - `challenge_settings`: Challenge configuration
  - `challenge_participants`: Player-challenge relationships
  - `player_challenge_data`: Saved player state per challenge
  - `randomizer_mappings`: Block randomizer mappings
  - `schema_migrations`: Migration tracking

### Listeners
Event handlers for gameplay mechanics:
- **PlayerConnectionListener**: Handle join/quit, restore challenge state on reconnect
- **PlayerHealthListener**: Death handling, spectator mode on death (hardcore mechanic)
- **DragonDefeatListener**: Challenge completion trigger
- **PortalListener**: Manage nether/end portal travel between challenge dimensions
- **ExperienceBorderListener**: Player-specific world borders that grow with XP levels
- **BlockDropListener**: Block randomizer implementation
- **LobbyProtectionListener**: Prevent block changes in lobby
- **ChatListener**: Challenge-specific chat isolation

### Utilities
- **LanguageManager**: i18n support (English/German), player language preferences stored in memory
- **TimeFormatter**: Duration formatting for challenge timers
- **WorldTimeFormatter**: In-game world time formatting

## Database Migration Guidelines

### Creating a New Migration
When adding new database migrations:

1. **File Location**: Place new migrations in `src/main/kotlin/li/angu/challengeplugin/database/migrations/`
2. **Naming Convention**: Use format `Migration{XXX}{DescriptiveAction}.kt` where XXX is zero-padded version number
3. **Structure**: Extend `Migration` interface, implement `version`, `description`, and `execute()`
4. **Registration**: Add your new migration to the list in `MigrationManager.kt` (around line 22-33)
5. **Version Numbers**: Always increment version number sequentially - never modify existing migrations
6. **Best Practices**: Use `IF NOT EXISTS` for CREATE statements, batch multiple statements with `executeBatch()`, and test thoroughly
7. **Examples**: Check existing migrations (`Migration001CreateInitialTables.kt`, `Migration002AddChallengeSettings.kt`) for structure

### Database Query Guidelines

**NEVER use `INSERT OR REPLACE`** - This SQL command deletes and recreates records, which:
- Breaks foreign key relationships and cascades
- Can cause data loss in related tables
- Triggers unnecessary constraint violations

**Instead use proper INSERT/UPDATE logic**:
- Check if record exists first with a SELECT query
- Use INSERT for new records, UPDATE for existing records
- Maintain referential integrity and avoid data loss
- See `ChallengeManager.saveChallengeToDatabase()` for reference implementation

Example pattern:
```kotlin
val exists = checkIfRecordExists(id)
val query = if (exists) {
    "UPDATE table SET ... WHERE id = ?"
} else {
    "INSERT INTO table (...) VALUES (...)"
}
```

## Challenge World Management

### World Lifecycle
1. **Creation**: Challenges created with `createChallenge()` don't immediately create worlds
2. **Finalization**: After settings configured, `finalizeChallenge()` checks for pre-generated world or creates new one
3. **Loading**: Worlds loaded on-demand when players join, includes all 3 dimensions (overworld, nether, end)
4. **Unloading**: Worlds automatically unload after 5 minutes empty (see `Challenge.isReadyForUnload()`)
5. **Deletion**: `deleteChallenge()` unloads worlds, deletes database entries, removes world folders

### World Preparation
- `/prepare` command integrates with Chunky plugin to pre-generate worlds
- Pre-generated worlds assigned to challenges when finalized
- Reduces lag when starting new challenges

## Key Behavioral Patterns

### Player State Management
- When joining challenge: Player data saved if exists, otherwise fresh start with optional starter kit
- When leaving challenge: Inventory cleared, effects removed, health/food reset
- On disconnect: Player data saved if in challenge
- On reconnect: Player data restored if they were in a challenge

### Challenge Timer System
- Timer starts when first player joins
- Timer pauses when last player leaves (with timestamp tracking)
- Total paused duration tracked separately from active time
- `getEffectiveDuration()` returns actual active playtime

### Hardcore Mechanics
- On death: Player set to SPECTATOR mode (via `PlayerHealthListener`)
- Dead players remain in spectator, can observe but not interact
- Challenge fails if all players die
- Challenge completes when Ender Dragon defeated (via `DragonDefeatListener`)

## Language/i18n System
- Language files in `src/main/resources/lang/` (en.yml, de.yml)
- Use `languageManager.getMessage(key, player, "placeholder" to "value")` for all user-facing text
- Player language preferences stored in memory (not persisted across restarts)
- Always add translations for both English and German when adding new messages
