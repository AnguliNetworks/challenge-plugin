package li.angu.challengeplugin.listeners

import li.angu.challengeplugin.ChallengePluginPlugin
import org.bukkit.entity.IronGolem
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityTargetEvent
import org.bukkit.event.entity.EntityTeleportEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.entity.EntityCombustEvent

/**
 * Handles interactions with Challenge NPCs
 */
class NPCListener(private val plugin: ChallengePluginPlugin) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val entity = event.rightClicked
        val player = event.player

        // Check if the entity is a challenge NPC
        if (!plugin.npcManager.isNPC(entity)) {
            return
        }

        event.isCancelled = true

        // Check if player is in lobby world
        if (!plugin.lobbyManager.isLobbyWorld(player.world.name)) {
            player.sendMessage(plugin.languageManager.getMessage("npc.not_in_lobby", player))
            return
        }

        // Check if player is already in a challenge
        val currentChallenge = plugin.challengeManager.getPlayerChallenge(player)
        if (currentChallenge != null) {
            player.sendMessage(plugin.languageManager.getMessage("npc.already_in_challenge", player))
            return
        }

        // Get the NPC data
        val npc = plugin.npcManager.getNPCByEntityUuid(entity.uniqueId)
        if (npc == null) {
            player.sendMessage(plugin.languageManager.getMessage("npc.not_found_entity", player))
            plugin.logger.warning("NPC entity ${entity.uniqueId} found but no database entry exists")
            return
        }

        // Get the challenge
        val challenge = plugin.challengeManager.getChallenge(npc.challengeId)
        if (challenge == null) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "npc.challenge_not_found",
                    player,
                    "id" to npc.challengeId.toString()
                )
            )
            return
        }

        // Join the challenge
        val success = plugin.challengeManager.joinChallenge(player, challenge)
        if (success) {
            player.sendMessage(
                plugin.languageManager.getMessage(
                    "npc.joined_via_npc",
                    player,
                    "name" to challenge.name
                )
            )
        } else {
            player.sendMessage(plugin.languageManager.getMessage("challenge.join_failed", player))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        val entity = event.entity

        // Protect NPCs from damage
        if (plugin.npcManager.isNPC(entity)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityTarget(event: EntityTargetEvent) {
        val entity = event.entity

        // Prevent NPCs from targeting anything
        if (plugin.npcManager.isNPC(entity)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityTeleport(event: EntityTeleportEvent) {
        val entity = event.entity

        // Prevent NPCs from teleporting
        if (plugin.npcManager.isNPC(entity)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityCombust(event: EntityCombustEvent) {
        val entity = event.entity

        // Prevent NPCs from burning
        if (plugin.npcManager.isNPC(entity)) {
            event.isCancelled = true
        }
    }
}
