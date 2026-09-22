package com.jqshuv.deathban.listeners;

import com.jqshuv.deathban.DeathBan;
import com.jqshuv.deathban.utils.Scheduler;
import com.jqshuv.deathban.utils.TimeUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DeathListener implements Listener {

    // ConcurrentHashMap : la carte peut être lue/modifiée depuis plusieurs threads
    // (respawn, quit, tâches planifiées) en même temps sous Folia.
    private static final ConcurrentHashMap<UUID, PendingBan> pendingBans = new ConcurrentHashMap<>();

    private static class PendingBan {
        final long scheduledTime;
        final boolean banSpectator;
        final boolean doIpBan;
        final Date banExpiry;
        final String banReason;
        final String playerName;
        final String cachedIp; // capturé au moment de la mort, tant que le joueur est encore en ligne

        PendingBan(long scheduledTime, boolean banSpectator, boolean doIpBan, Date banExpiry,
                   String banReason, String playerName, String cachedIp) {
            this.scheduledTime = scheduledTime;
            this.banSpectator = banSpectator;
            this.doIpBan = doIpBan;
            this.banExpiry = banExpiry;
            this.banReason = banReason;
            this.playerName = playerName;
            this.cachedIp = cachedIp;
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity().getPlayer();
        assert p != null;

        DeathBan.debug("=== Player Death Event ===");
        DeathBan.debug("Player: " + p.getName() + " (UUID: " + p.getUniqueId() + ")");
        DeathBan.debug("Death cause: " + e.getEntity().getLastDamageCause());
        DeathBan.debug("Killer: " + (p.getKiller() != null ? p.getKiller().getName() : "None"));

        FileConfiguration fl = DeathBan.getInstance().getCustomConfig();
        if (!p.hasPermission("deathban.immune") || fl.getBoolean("settings.ignore-permission")) {
            DeathBan.debug("Player does not have immunity or immunity is ignored");

            boolean playerKillOnly = fl.getBoolean("settings.player-kill-only");
            DeathBan.debug("Player-kill-only mode: " + playerKillOnly);

            if (playerKillOnly && p.getKiller() == null) {
                DeathBan.debug("Skipping ban: Player was not killed by another player");
                return;
            }

            int tillBan = fl.getInt("settings.ban-delay");
            boolean banSpectator = fl.getBoolean("settings.spectator-after-death");
            boolean doIpBan = fl.getBoolean("settings.ban-ip");
            int banTime = fl.getInt("settings.ban-time");
            Date date = new Date();

            DeathBan.debug("Ban delay: " + tillBan + " seconds");
            DeathBan.debug("Spectator mode: " + banSpectator);
            DeathBan.debug("IP ban: " + doIpBan);
            DeathBan.debug("Ban time: " + banTime + " minutes");

            if (banSpectator) {
                p.setGameMode(GameMode.SPECTATOR);
                DeathBan.debug("Set player to spectator mode");
            }

            if (banTime == 0) {
                date = null;
                DeathBan.debug("Permanent ban (no expiry)");
            } else if (banTime > 0) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                cal.add(Calendar.MINUTE, banTime);
                date = cal.getTime();
                DeathBan.debug("Ban will expire at: " + date);
            }

            Date finalDate = date;
            String banReason = fl.getString("settings.banreason");
            DeathBan.debug("Ban reason: " + banReason);

            // Capture l'IP tout de suite pendant que le joueur est encore en ligne,
            // pour pouvoir bannir son IP même s'il se déconnecte avant l'exécution du ban.
            String cachedIp = doIpBan && p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : null;

            // Annonce dans le tchat + son, et supprime le message de mort par défaut de Minecraft
            announceUpcomingBan(p, e, fl, tillBan);

            long scheduledTime = System.currentTimeMillis() + (tillBan * 1000L);
            DeathBan.debug("Scheduling ban execution for: " + new Date(scheduledTime));

            PendingBan ban = new PendingBan(scheduledTime, banSpectator, doIpBan, finalDate, banReason, p.getName(), cachedIp);
            pendingBans.put(p.getUniqueId(), ban);
            DeathBan.debug("Total pending bans in queue: " + pendingBans.size());
            DeathBan.debug("=== End Player Death Event ===");
        } else {
            DeathBan.debug("Player has immunity and ignore-permission is false - skipping ban");
            DeathBan.debug("=== End Player Death Event ===");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        UUID playerId = p.getUniqueId();

        DeathBan.debug("=== Player Respawn Event ===");
        DeathBan.debug("Player: " + p.getName() + " (UUID: " + playerId + ")");

        if (!pendingBans.containsKey(playerId)) {
            DeathBan.debug("No pending ban found - skipping");
            DeathBan.debug("=== End Player Respawn Event ===");
            return;
        }

        PendingBan ban = pendingBans.get(playerId);
        long currentTime = System.currentTimeMillis();
        long delay = Math.max(0, ban.scheduledTime - currentTime);
        long delayTicks = Math.max(1L, delay / 50);

        DeathBan.debug("Scheduling visual reset + ban for " + delayTicks + " ticks later");
        Scheduler.runDelayed(p, () -> resolvePendingBan(playerId, true), delayTicks);

        DeathBan.debug("=== End Player Respawn Event ===");
    }

    /**
     * Le joueur se déconnecte : s'il a un ban en attente, on le bannit tout de suite
     * plutôt que d'attendre une tâche planifiée qui ne se déclenchera jamais tant qu'il est hors ligne.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID playerId = e.getPlayer().getUniqueId();
        if (!pendingBans.containsKey(playerId)) return;

        DeathBan.debug("Player " + e.getPlayer().getName() + " disconnected with a pending ban - executing immediately");
        resolvePendingBan(playerId, false);
    }

    /**
     * Filet de sécurité : si un joueur se retrouve coincé en mode spectateur au moment où il se
     * connecte (ban déjà exécuté/expiré pendant qu'il était hors ligne, remise en survie jamais faite),
     * on le remet en survie proprement.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID playerId = p.getUniqueId();

        // Un ban est encore en attente pour ce joueur (cas très rare) : on ne touche à rien,
        // resolvePendingBan s'en chargera via onRespawn/onQuit.
        if (pendingBans.containsKey(playerId)) return;

        if (p.getGameMode() == GameMode.SPECTATOR) {
            FileConfiguration fl = DeathBan.getInstance().getCustomConfig();
            if (fl.getBoolean("settings.spectator-after-death", true)) {
                DeathBan.debug("Safety net: " + p.getName() + " joined stuck in spectator mode - resetting to survival");
                p.setGameMode(GameMode.SURVIVAL);
                p.setHealth(20.0);
                p.setFoodLevel(20);
                Scheduler.teleportAsync(p, p.getWorld().getSpawnLocation());
            }
        }
    }

    /**
     * Récupère et retire le ban en attente (opération atomique : un seul appelant "gagne"
     * si onRespawn et onQuit se déclenchent presque en même temps), puis l'applique.
     */
    private void resolvePendingBan(UUID playerId, boolean doVisualReset) {
        PendingBan ban = pendingBans.remove(playerId);
        if (ban == null) {
            DeathBan.debug("resolvePendingBan: no ban claimed for " + playerId + " (already handled elsewhere)");
            return;
        }

        Player p = Bukkit.getPlayer(playerId);

        if (doVisualReset && p != null && p.isOnline() && ban.banSpectator) {
            p.setGameMode(GameMode.SURVIVAL);
            p.setHealth(20.0);
            p.setFoodLevel(20);
            Scheduler.teleportAsync(p, p.getWorld().getSpawnLocation());
            DeathBan.debug("Restored player to survival mode and teleported to spawn before ban");
        }

        applyBan(p, ban);
    }

    /**
     * Ajoute réellement l'entrée de ban. Ne dépend jamais du statut en ligne du joueur :
     * un OfflinePlayer peut être banni tout aussi bien qu'un joueur connecté.
     */
    private void applyBan(Player onlinePlayerOrNull, PendingBan ban) {
        DeathBan.debug("=== Applying Ban for " + ban.playerName + " ===");

        String plainReason = "You are banned from this server.";
        FileConfiguration fl = DeathBan.getInstance().getCustomConfig();
        String timeText = ban.banExpiry != null
                ? TimeUtils.formatDuration(ban.banExpiry.getTime() - System.currentTimeMillis())
                : fl.getString("settings.permanent-label", "Permanent");
        String dynamicReason = ban.banReason
                .replace("{time}", timeText)
                .replace("{player}", ban.playerName);

        if (ban.doIpBan && ban.cachedIp != null) {
            DeathBan.debug("Adding IP ban for: " + ban.cachedIp);
            Bukkit.getBanList(org.bukkit.BanList.Type.IP).addBan(ban.cachedIp, plainReason, ban.banExpiry, "console");
        } else {
            DeathBan.debug("Adding name ban for: " + ban.playerName);
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(ban.playerName, plainReason, ban.banExpiry, "console");
        }

        if (onlinePlayerOrNull != null && onlinePlayerOrNull.isOnline()) {
            Scheduler.kick(onlinePlayerOrNull, dynamicReason);
            DeathBan.debug("Player was online - kicked with dynamic reason");
        } else {
            DeathBan.debug("Player was offline - ban list entry added, no kick needed");
        }

        DeathBan.debug("=== End Applying Ban ===");
    }

    private void announceUpcomingBan(Player p, PlayerDeathEvent e, FileConfiguration fl, int tillBan) {
        boolean enabled = fl.getBoolean("settings.chat-announcement.enabled", true);
        if (!enabled) return;

        // Récupère le message de mort natif de Minecraft ("X a été tué par Y", "X est mort dans la lave", etc.)
        Component deathMessageComponent = e.deathMessage();
        String deathMessage = deathMessageComponent != null
                ? PlainTextComponentSerializer.plainText().serialize(deathMessageComponent)
                : p.getName() + " est mort";

        // Empêche Minecraft d'afficher AUSSI son propre message de mort par défaut,
        // puisqu'on diffuse notre propre version stylisée juste en dessous.
        e.deathMessage(null);

        String template = fl.getString(
                "settings.chat-announcement.message",
                "<dark_red><bold>☠</bold> {deathmessage}</dark_red> <gray>— banni dans {delay}s</gray>"
        );
        String formatted = template
                .replace("{deathmessage}", deathMessage)
                .replace("{player}", p.getName())
                .replace("{delay}", String.valueOf(tillBan));

        Component message = DeathBan.getMiniMessage().deserialize(formatted);
        Bukkit.getServer().sendMessage(message);
        DeathBan.debug("Chat announcement broadcasted for " + p.getName() + " (" + deathMessage + ")");

        String soundName = fl.getString("settings.chat-announcement.sound", "ENTITY_LIGHTNING_BOLT_THUNDER");
        float volume = (float) fl.getDouble("settings.chat-announcement.sound-volume", 1.0);
        float pitch = (float) fl.getDouble("settings.chat-announcement.sound-pitch", 1.0);

        try {
            Sound sound = Sound.valueOf(soundName);
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.playSound(online.getLocation(), sound, volume, pitch);
            }
            DeathBan.debug("Thunder sound played: " + soundName);
        } catch (IllegalArgumentException ex) {
            DeathBan.getInstance().getLogger().warning("Son invalide dans la config (settings.chat-announcement.sound): " + soundName);
        }
    }
}
