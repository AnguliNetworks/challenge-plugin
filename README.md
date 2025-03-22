# ChallengePlugin

A Minecraft Spigot/Paper plugin for creating and managing challenges with hardcore Ender Dragon fights.

## Features

- Create isolated challenge worlds with separate inventories
- Track player progress and challenge completion
- Implement hardcore-style gameplay (spectator mode on death)
- Track challenge duration with automatic pause when no players are active
- Multilingual support (English and German)
- Defeat the Ender Dragon to complete challenges

## Commands

| Command | Description |
|---------|-------------|
| `/challenge create <name>` | Create a new challenge |
| `/challenge list` | List all available challenges |
| `/challenge join <id>` | Join a specific challenge |
| `/challenge leave` | Leave your current challenge |
| `/challenge info [id]` | Display information about a challenge |
| `/lang <language>` | Change your preferred language |

## Permissions

| Permission | Description |
|------------|-------------|
| `challengeplugin.command.challenge` | Access to all challenge commands |
| `challengeplugin.command.language` | Ability to change language |

## Installation

1. Download the latest release JAR from the releases page
2. Place the JAR in your server's `plugins` directory
3. Restart your server
4. Configure the plugin as needed

## Building from Source

```bash
./gradlew build
```

The compiled JAR will be available in `build/libs/`.

## Development

### Requirements

- JDK 11 or higher
- Gradle

### Build Commands

- Build: `./gradlew build`
- Run tests: `./gradlew test`
- Single test: `./gradlew test --tests "li.angu.challengeplugin.utils.TimeFormatterTest"`
- Clean build: `./gradlew clean build`
- Build JAR: `./gradlew jar`
