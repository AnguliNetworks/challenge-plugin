package li.angu.challengeplugin.listeners

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.Material
import org.bukkit.block.Container
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.inventory.ItemStack
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BlockDropListener(private val plugin: ChallengePluginPlugin) : Listener {

    // Map to store random block drops by challenge ID
    // ChallengeID -> (Block Material -> Random Material)
    private val randomDropsMap = ConcurrentHashMap<UUID, ConcurrentHashMap<Material, Material>>()
    private val random = Random()

    init {
        loadRandomizerMappings()
    }

    /**
     * Get a cached random material for the given challenge and block material
     * If no mapping exists, create a new random mapping
     */
    private fun getRandomMaterial(challengeId: UUID, blockMaterial: Material): Material {
        // If we don't have a map for this challenge yet, create one
        val challengeMap = randomDropsMap.computeIfAbsent(challengeId) { ConcurrentHashMap() }

        // Get or create a random material mapping for this block material
        return challengeMap.computeIfAbsent(blockMaterial) {
            // Get a list of all valid materials that can be items
            val validMaterials = Material.values().filter {
                it.isItem && !it.isAir
            }

            // Return a random material
            val randomMaterial = validMaterials[random.nextInt(validMaterials.size)]

            // Save the new mapping to database
            plugin.databaseDriver.executeUpdate(
                "INSERT OR REPLACE INTO randomizer_mappings (challenge_id, source_material, target_material) VALUES (?, ?, ?)",
                challengeId.toString(),
                blockMaterial.name,
                randomMaterial.name
            )

            randomMaterial
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        // Get the player's challenge
        val challenge = plugin.challengeManager.getPlayerChallenge(player) ?: return

        // Skip if block randomizer is not enabled for this challenge
        if (!challenge.settings.blockRandomizer) return

        // Skip if the player is not in survival mode
        if (!player.gameMode.name.equals("SURVIVAL", ignoreCase = true)) return

        // Get the block being broken
        val block = event.block

        // Skip if the block type is air
        if (block.type.isAir) return

        // Skip if the block is a container
        if (block.state is Container) return

        // Get the original drops that would normally happen
        val drops = block.getDrops(player.inventory.itemInMainHand, player)

        // Calculate total number of items that would drop normally but minimum 1
        val totalDropAmount = drops.sumOf { it.amount }.coerceAtLeast(1)

        // Cancel the original drops
        event.isDropItems = false

        // Get the randomized material based on the block type (not the drops)
        val randomMaterial = getRandomMaterial(challenge.id, block.type)

        // Create random item with at least the same amount as original drops
        val randomItem = ItemStack(randomMaterial, totalDropAmount)

        // Drop the randomized item
        block.world.dropItemNaturally(block.location, randomItem)
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        if (event.blockList().isEmpty()) return

        // Find a player in the challenge in this world
        val worldPlayers = event.entity.world.players
        var challengeId: UUID? = null

        // Try to find a player in a challenge in this world
        for (player in worldPlayers) {
            val challenge = plugin.challengeManager.getPlayerChallenge(player)
            if (challenge != null && challenge.settings.blockRandomizer) {
                challengeId = challenge.id
                break
            }
        }

        // Skip if not in a challenge world with block randomizer enabled
        if (challengeId == null) return

        // Process each block in the explosion
        val blockList = event.blockList().toList() // Create a copy to avoid ConcurrentModificationException

        // Set yields to 0 so vanilla drops don't happen
        event.yield = 0f

        // Custom drop chance - make it natural (around 30% chance for a block to drop items)
        val customDropChance = 0.3f

        for (block in blockList) {
            // Skip if the block is a container
            if (block.state is Container) continue

            // Skip air blocks
            if (block.type.isAir) continue

            // Get normal drops (with a null player/tool)
            val drops = block.getDrops()

            // Skip if no drops
            if (drops.isEmpty()) continue

            // Calculate total number of items that would drop
            val totalDropAmount = drops.sumOf { it.amount }.coerceAtLeast(1)

            // Apply our custom explosion probability
            if (random.nextFloat() <= customDropChance) {
                // Get the randomized material based on the block type
                val randomMaterial = getRandomMaterial(challengeId, block.type)

                // Create random item with same amount as original drops
                val randomItem = ItemStack(randomMaterial, totalDropAmount)

                // Drop the randomized item
                block.world.dropItemNaturally(block.location, randomItem)
            }
        }
    }

    /**
     * Loads randomizer mappings from database for all saved challenges
     */
    private fun loadRandomizerMappings() {
        try {
            plugin.databaseDriver.executeQuery(
                "SELECT challenge_id, source_material, target_material FROM randomizer_mappings"
            ) { rs ->
                while (rs.next()) {
                    try {
                        val challengeId = UUID.fromString(rs.getString("challenge_id"))
                        val sourceMaterial = Material.valueOf(rs.getString("source_material"))
                        val targetMaterial = Material.valueOf(rs.getString("target_material"))

                        val challengeMap = randomDropsMap.computeIfAbsent(challengeId) { ConcurrentHashMap() }
                        challengeMap[sourceMaterial] = targetMaterial

                    } catch (e: Exception) {
                        plugin.logger.warning("Failed to load randomizer mapping from database: ${e.message}")
                    }
                }
            }

            if (randomDropsMap.isNotEmpty()) {
                val totalMappings = randomDropsMap.values.sumOf { it.size }
                plugin.logger.info("Loaded $totalMappings randomizer mappings for ${randomDropsMap.size} challenges")
            }

        } catch (e: Exception) {
            plugin.logger.warning("Failed to load randomizer mappings from database: ${e.message}")
        }
    }

    /**
     * Saves all randomizer mappings to database
     */
    fun saveRandomizerMappings() {
        randomDropsMap.forEach { (challengeId, challengeMap) ->
            try {
                val operations = mutableListOf<Pair<String, Array<Any?>>>()

                // Clear existing mappings for this challenge
                operations.add(
                    "DELETE FROM randomizer_mappings WHERE challenge_id = ?" to arrayOf<Any?>(challengeId.toString())
                )

                // Save all material mappings
                challengeMap.forEach { (source, target) ->
                    operations.add(
                        "INSERT INTO randomizer_mappings (challenge_id, source_material, target_material) VALUES (?, ?, ?)" to arrayOf<Any?>(
                            challengeId.toString(),
                            source.name,
                            target.name
                        )
                    )
                }

                if (plugin.databaseDriver.executeTransaction(operations)) {
                    plugin.logger.info("Saved ${challengeMap.size} randomizer mappings for challenge $challengeId")
                } else {
                    plugin.logger.warning("Failed to save randomizer mappings for challenge $challengeId")
                }
            } catch (e: Exception) {
                plugin.logger.warning("Failed to save randomizer mappings for challenge $challengeId: ${e.message}")
            }
        }
    }
}
