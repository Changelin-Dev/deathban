# 💀 DeathBan (fork Changelin-Dev)

A lightweight and simple plugin that bans players when they die. Perfect for hardcore survival servers!

Ce fork ajoute une annonce dans le tchat avec son au moment de la mort, et un message de ban qui affiche en temps réel le temps restant.

**Got ideas or suggestions? Join our Discord!**  

[![discord](https://img.shields.io/discord/903750807957147718?color=7289da&label=discord&logo=discord&logoColor=white)](https://discord.com/invite/BeYVQ3fhF4)

---

## ✨ Features

- 🎮 **Simple & Lightweight** - Minimal resource usage
- 🔥 **Folia Support** - Works on modern Folia servers
- ⚔️ **PvP Mode** - Ban only when killed by other players
- 🎨 **Colorful Ban Messages** - Support for Adventure API with colors and formatting
- ⏱️ **Flexible Timing** - Customizable ban delay and duration
- 👁️ **Spectator Mode** - Optional spectator mode after death
- 🔐 **IP Banning** - Choose between player or IP bans
- 📢 **Chat Announcement** - Broadcasts the death message (killed by X, fell, lava, etc.) with a sound when a ban is triggered
- ⏳ **Live Countdown** - The ban message shows the real remaining time, recalculated on every login attempt

---

## 📋 Configuration

```yaml
settings:
  banreason: "<bold><red>Game Over!</red></bold>\n\n<gray>You were banned for dying in combat.\n\nBan Duration: <yellow>{time}</yellow>\nBan Type: <aqua>Deathban</aqua></gray>"
  ban-delay: 3                # Seconds after death until ban
  spectator-after-death: true # Enable spectator mode after death
  ban-time: 30                # Ban duration in minutes (0 = permanent)
  ban-ip: false               # Ban IP address instead of player name
  ignore-permission: false    # Ignore deathban.immune permission
  player-kill-only: false     # Only ban when killed by other players
  debug: false                # Enable debug logging
  permanent-label: "Permanent" # Shown instead of a duration when ban-time is 0

  chat-announcement:
    enabled: true
    message: "<dark_red><bold>☠</bold> {deathmessage}</dark_red> <gray>— banni dans {delay}s</gray>"
    sound: "ENTITY_LIGHTNING_BOLT_THUNDER"
    sound-volume: 1.0
    sound-pitch: 1.0

  login-ban-message: "<bold><red>Vous êtes banni de ce serveur</red></bold>\n\n<gray>Raison : <yellow>{reason}</yellow>\nTemps restant : <aqua>{time}</aqua></gray>"
```

### Color & Format Support

Message fields support Adventure API MiniMessage formatting:

**Colors:** `<red>`, `<blue>`, `<green>`, `<yellow>`, `<aqua>`, `<light_purple>`, `<gold>`, `<gray>`

**Formatting:** `<bold>`, `<italic>`, `<underlined>`, `<strikethrough>`

### Placeholders

| Placeholder | Available in | Description |
|---|---|---|
| `{time}` | `banreason`, `login-ban-message` | Remaining ban duration, recalculated live |
| `{player}` | `banreason`, `chat-announcement.message`, `login-ban-message` | Player name |
| `{deathmessage}` | `chat-announcement.message` | Native Minecraft death message (killed by X, fell, lava, etc.) |
| `{delay}` | `chat-announcement.message` | Seconds before the ban executes (`ban-delay`) |
| `{reason}` | `login-ban-message` | Raw stored ban reason |

**Example:**
```yaml
banreason: "<bold><red>BANNED!</red></bold>\n<yellow>Reason: <gray>Died in combat</gray></yellow>"
```

---

## 🔑 Permissions

| Permission      | Description                     |
|-----------------|---------------------------------|
| `deathban.immune` | Immunity from death bans        |

---

## 📝 Changelog

### Version 1.4.0 (fork)
- ✅ Chat announcement + sound when a ban is triggered, using the native death message
- ✅ Live countdown in the ban message (recalculated on every login attempt, not a static string)

### Version 1.3.0
- ✅ Folia Support
- ✅ Player-Kill-Only Mode
- ✅ Better Ban Messages (Adventure API)
- ✅ Debug Mode
- ✅ Ban Timing Fixed
- ✅ Safer Teleportation

---

## 🚀 Installation

1. Download the latest `DeathBan.jar` from [Releases](../../releases)
2. Place it in your `plugins` folder
3. Restart your server
4. Configure `plugins/DeathBan/config.yml` to your needs

**Enjoy! 💀**

