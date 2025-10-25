package li.angu.challengeplugin.models

/**
 * Difficulty levels for challenges.
 *
 * PEACEFUL, EASY, NORMAL, HARD: Standard Minecraft difficulties with normal respawn mechanics
 * HARDCORE: Hard difficulty + permanent death (spectator mode on death)
 */
enum class Difficulty {
    PEACEFUL,
    EASY,
    NORMAL,
    HARD,
    HARDCORE
}
