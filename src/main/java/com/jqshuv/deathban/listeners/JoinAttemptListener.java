package com.jqshuv.deathban.listeners;

import com.jqshuv.deathban.DeathBan;
import com.jqshuv.deathban.utils.TimeUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.Date;

/**
 * Recalcule le temps de ban restant à CHAQUE tentative de connexion,
 * pour que le joueur voie toujours un temps exact plutôt qu'un texte figé.
 */
public class JoinAttemptListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String playerName = event.getName();

        @SuppressWarnings("deprecation")
        BanList nameBanList = Bukkit.getBanList(BanList.Type.NAME);
        BanEntry entry = nameBanList.getBanEntry(playerName);

        if (entry == null) {
            String ip = event.getAddress().getHostAddress();
            @SuppressWarnings("deprecation")
            BanList ipBanList = Bukkit.getBanList(BanList.Type.IP);
            entry = ipBanList.getBanEntry(ip);
        }

        if (entry == null) return; // pas banni, on laisse passer

        FileConfiguration fl = DeathBan.getInstance().getCustomConfig();
        Date expiry = entry.getExpiration();
        String timeText;

        if (expiry == null) {
            timeText = fl.getString("settings.permanent-label", "Permanent");
        } else {
            long remaining = expiry.getTime() - System.currentTimeMillis();
            if (remaining <= 0) {
                // Le ban a techniquement expiré (Bukkit ne l'a pas encore purgé) -> on laisse entrer
                return;
            }
            timeText = TimeUtils.formatDuration(remaining);
        }

        String reason = entry.getReason() != null ? entry.getReason() : "";
        String template = fl.getString(
                "settings.login-ban-message",
                "<bold><red>Vous êtes banni.</red></bold>\n<gray>Temps restant : <yellow>{time}</yellow></gray>"
        );

        String formatted = template
                .replace("{time}", timeText)
                .replace("{reason}", reason)
                .replace("{player}", playerName);

        Component component = DeathBan.getMiniMessage().deserialize(formatted);
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, component);
        DeathBan.debug("Message de ban dynamique envoyé à " + playerName + " (reste " + timeText + ")");
    }
}
