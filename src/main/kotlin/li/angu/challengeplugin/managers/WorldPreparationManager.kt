package li.angu.challengeplugin.managers

import li.angu.challengeplugin.ChallengePluginPlugin
import li.angu.challengeplugin.models.Challenge
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.GameRule
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.logging.Level

/**
 * Manages pre-generated worlds for challenges to reduce world generation lag.
 */
class WorldPreparationManager(private val plugin: ChallengePluginPlugin) {

    private val worldsFolder = File(plugin.dataFolder, "worlds")
    
    // Initializes the manager and creates the worlds directory if it doesn't exist
    init {
        worldsFolder.mkdirs()
        plugin.logger.info("WorldPreparationManager initialized. Worlds folder: ${worldsFolder.absolutePath}")
        
        // Count how many worlds are available
        val worldCount = countAvailableWorlds()
        plugin.logger.info("Found $worldCount pre-generated world sets available")
    }
    
    /**
     * Counts how many pre-generated worlds are available.
     */
    fun countAvailableWorlds(): Int {
        return worldsFolder.listFiles()?.filter { it.isDirectory }?.size ?: 0
    }
    
    /**
     * Creates a new pre-generated world set asynchronously.
     * Returns a CompletableFuture that completes when the world creation is done.
     */
    fun prepareWorldAsync(): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        
        // Run world generation in a separate thread to avoid blocking the main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val worldId = UUID.randomUUID().toString().substring(0, 8)
            val worldName = "prep_$worldId"
            
            plugin.logger.info("Starting preparation of world set: $worldName")
            
            // Switch back to the main thread for actual world creation
            // as Bukkit API is not thread-safe
            Bukkit.getScheduler().runTask(plugin, Runnable {
                try {
                    // Create the main world
                    val creator = WorldCreator(worldName)
                    val world = creator.createWorld()
                    
                    if (world == null) {
                        plugin.logger.warning("Failed to create preparation world: $worldName")
                        future.complete(false)
                        return@Runnable
                    }
                    
                    // Configure the world
                    world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
                    world.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false)
                    world.difficulty = org.bukkit.Difficulty.HARD
                    
                    val worldSeed = world.seed
                    
                    // Create nether world
                    val netherName = "${worldName}_nether"
                    val netherCreator = WorldCreator(netherName)
                        .environment(World.Environment.NETHER)
                        .seed(worldSeed)
                    val netherWorld = netherCreator.createWorld()
                    
                    // Create end world
                    val endName = "${worldName}_the_end"
                    val endCreator = WorldCreator(endName)
                        .environment(World.Environment.THE_END)
                        .seed(worldSeed)
                    val endWorld = endCreator.createWorld()
                    
                    // Apply the same settings to nether and end worlds
                    netherWorld?.apply {
                        setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
                        setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false)
                        difficulty = org.bukkit.Difficulty.HARD
                    }
                    
                    endWorld?.apply {
                        setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
                        setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false)
                        difficulty = org.bukkit.Difficulty.HARD
                    }
                    
                    // Run this on main thread after a delay to ensure spawn chunks are generated
                    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                        // Unload all the worlds
                        Bukkit.unloadWorld(world, true)
                        netherWorld?.let { Bukkit.unloadWorld(it, true) }
                        endWorld?.let { Bukkit.unloadWorld(it, true) }
                        
                        // Now move these worlds to our preparation folder
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                            try {
                                // Get server's world container (where worlds are saved)
                                val serverWorldContainer = plugin.server.worldContainer
                                
                                // Create destination folders in our plugin's worlds folder
                                val destMainFolder = File(worldsFolder, worldName)
                                val destNetherFolder = File(worldsFolder, netherName)
                                val destEndFolder = File(worldsFolder, endName)
                                
                                destMainFolder.mkdirs()
                                destNetherFolder.mkdirs()
                                destEndFolder.mkdirs()
                                
                                // Move worlds from server folder to our plugin's folder
                                val sourceMainFolder = File(serverWorldContainer, worldName)
                                val sourceNetherFolder = File(serverWorldContainer, netherName)
                                val sourceEndFolder = File(serverWorldContainer, endName)
                                
                                if (moveWorldFolder(sourceMainFolder, destMainFolder) &&
                                    moveWorldFolder(sourceNetherFolder, destNetherFolder) &&
                                    moveWorldFolder(sourceEndFolder, destEndFolder)) {
                                    plugin.logger.info("Successfully prepared world set: $worldName")
                                    future.complete(true)
                                } else {
                                    plugin.logger.warning("Failed to move some world folders for: $worldName")
                                    future.complete(false)
                                }
                            } catch (e: Exception) {
                                plugin.logger.log(Level.SEVERE, "Error during world preparation: ${e.message}", e)
                                future.complete(false)
                            }
                        })
                    }, 100L) // Wait 5 seconds to make sure spawn chunks are generated
                    
                } catch (e: Exception) {
                    plugin.logger.log(Level.SEVERE, "Error during world preparation: ${e.message}", e)
                    future.complete(false)
                }
            })
        })
        
        return future
    }
    
    /**
     * Gets a prepared world set for a challenge.
     * Returns the world name if successful, null otherwise.
     */
    fun getWorldForChallenge(challengeId: UUID): String? {
        val availableWorlds = worldsFolder.listFiles()?.filter { it.isDirectory } ?: return null
        
        if (availableWorlds.isEmpty()) {
            plugin.logger.warning("No pre-generated worlds available for challenge ${challengeId}")
            return null
        }
        
        // Get the first available world
        val sourceWorldFolder = availableWorlds.first()
        val worldName = sourceWorldFolder.name
        val challengeWorldName = "challenge_${challengeId.toString().substring(0, 8)}"
        
        // Get associated nether and end world folders
        val sourceNetherFolder = File(worldsFolder, "${worldName}_nether")
        val sourceEndFolder = File(worldsFolder, "${worldName}_the_end")
        
        // Target folders in the server world container
        val serverWorldContainer = plugin.server.worldContainer
        val targetMainFolder = File(serverWorldContainer, challengeWorldName)
        val targetNetherFolder = File(serverWorldContainer, "${challengeWorldName}_nether")
        val targetEndFolder = File(serverWorldContainer, "${challengeWorldName}_the_end")
        
        try {
            // Move the world folders to the server world container with the challenge name
            if (!moveWorldFolder(sourceWorldFolder, targetMainFolder)) {
                plugin.logger.warning("Failed to move main world folder for challenge ${challengeId}")
                return null
            }
            
            if (!moveWorldFolder(sourceNetherFolder, File(serverWorldContainer, "${challengeWorldName}_nether"))) {
                plugin.logger.warning("Failed to move nether world folder for challenge ${challengeId}")
                // Try to clean up
                deleteWorldFolder(targetMainFolder)
                return null
            }
            
            if (!moveWorldFolder(sourceEndFolder, File(serverWorldContainer, "${challengeWorldName}_the_end"))) {
                plugin.logger.warning("Failed to move end world folder for challenge ${challengeId}")
                // Try to clean up
                deleteWorldFolder(targetMainFolder)
                deleteWorldFolder(targetNetherFolder)
                return null
            }
            
            // Delete the original prepared world folders
            deleteWorldFolder(sourceWorldFolder)
            deleteWorldFolder(sourceNetherFolder)
            deleteWorldFolder(sourceEndFolder)
            
            plugin.logger.info("Successfully prepared world set: $challengeWorldName for challenge ${challengeId}")
            return challengeWorldName
            
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Error during world preparation for challenge ${challengeId}: ${e.message}", e)
            
            // Try to clean up if something went wrong
            deleteWorldFolder(targetMainFolder)
            deleteWorldFolder(targetNetherFolder)
            deleteWorldFolder(targetEndFolder)
            
            return null
        }
    }
    
    /**
     * Moves a world folder from one location to another.
     */
    private fun moveWorldFolder(source: File, destination: File): Boolean {
        if (!source.exists() || !source.isDirectory) {
            plugin.logger.warning("Source world folder does not exist: ${source.absolutePath}")
            return false
        }
        
        // Create the destination directory if it doesn't exist
        destination.mkdirs()
        
        try {
            // Copy all files and subdirectories
            source.listFiles()?.forEach { file ->
                val destFile = File(destination, file.name)
                if (file.isDirectory) {
                    // Recursively copy directory
                    file.copyRecursively(destFile, overwrite = true)
                } else {
                    // Copy file
                    file.copyTo(destFile, overwrite = true)
                }
            }
            
            // Delete the source folder after successful copy
            deleteWorldFolder(source)
            
            return true
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to move world folder ${source.name}: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Recursively deletes a world folder.
     */
    private fun deleteWorldFolder(worldFolder: File): Boolean {
        if (!worldFolder.exists()) {
            return true
        }
        
        try {
            // Delete all files and subdirectories
            worldFolder.deleteRecursively()
            return true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to delete world folder ${worldFolder.name}: ${e.message}")
            return false
        }
    }
}