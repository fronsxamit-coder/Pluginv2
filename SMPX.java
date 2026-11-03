package com.smpx;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SMP-X - all-in-one SMP admin toolkit.
 * Everything is used through the /smp command.
 */
@SuppressWarnings("deprecation")
public class SMPX extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {

    private static final String PREFIX = "&8[&bSMP-X&8] &r";
    private static final long PERMANENT = Long.MAX_VALUE;
    private static final int MAX_HOMES = 3;
    private static final String NO_REASON = "No reason given";

    /** One line of the /smp help list. */
    private record Help(String sub, String usage, String description) {
    }

    /** A ban. "until" is a time in milliseconds, or PERMANENT. */
    private record BanInfo(long until, String reason, String by) {
    }

    /** A mute. "until" is a time in milliseconds, or PERMANENT. */
    private record MuteInfo(long until, String reason, String by) {
    }

    /** An optional time typed after a player name. */
    private record TimeArg(long seconds, int next) {
    }

    // Order matters: it is the order shown in /smp.
    private static final List<Help> HELP = List.of(
            // Everyone
            new Help("ping", "/smp ping [player]", "Show ping (green = low, yellow = medium, red = high)"),
            new Help("playtime", "/smp playtime [player]", "Show playtime (all players if no name is given)"),
            new Help("sethome", "/smp sethome [name]", "Save a home where you stand (max " + MAX_HOMES + ")"),
            new Help("home", "/smp home [name]", "Teleport to your home"),
            new Help("delhome", "/smp delhome [name]", "Delete one of your homes"),
            new Help("homes", "/smp homes", "List your homes"),
            new Help("back", "/smp back", "Go back to your last location or death point"),
            new Help("night", "/smp night", "Turn permanent night vision on or off"),
            // Admins
            new Help("ban", "/smp ban <player> [time] [reason]", "Ban a player. Time: 30s, 10m, 2h, 1d or perm"),
            new Help("unban", "/smp unban <player>", "Unban a player"),
            new Help("banlist", "/smp banlist", "Show all banned players"),
            new Help("mute", "/smp mute <player> [time] [reason]", "Stop a player from chatting"),
            new Help("unmute", "/smp unmute <player>", "Let a player chat again"),
            new Help("jail", "/smp jail <player> [time]", "Send a player to jail"),
            new Help("unjail", "/smp unjail <player>", "Release a player from jail"),
            new Help("setjail", "/smp setjail", "Set the jail at your location"),
            new Help("freeze", "/smp freeze <player>", "Freeze a player in place"),
            new Help("unfreeze", "/smp unfreeze <player>", "Unfreeze a player"),
            new Help("vanish", "/smp vanish [player]", "Become invisible to normal players"),
            new Help("tp", "/smp tp <player> [toPlayer]", "Teleport to a player, or send one player to another"),
            new Help("tphere", "/smp tphere <player>", "Teleport a player to you"),
            new Help("feed", "/smp feed [player]", "Fill the hunger bar"),
            new Help("heal", "/smp heal [player]", "Heal a player to full health"),
            new Help("fly", "/smp fly [player]", "Turn flight on or off"),
            new Help("invsee", "/smp invsee <player>", "Open a player's inventory"),
            new Help("enderchest", "/smp enderchest <player>", "Open a player's ender chest"),
            new Help("lightning", "/smp lightning <player>", "Strike a player with one lightning bolt"),
            new Help("nick", "/smp nick <player> <newname|reset>", "Change a player's display name"),
            new Help("kill", "/smp kill <player>", "Kill a player"),
            new Help("keep-inventory", "/smp keep-inventory <player>", "Turn keep inventory on or off for one player"),
            new Help("keep-xp", "/smp keep-xp <player>", "Turn keep XP on or off for one player"),
            // Owner and OP
            new Help("god", "/smp god [player]", "God mode - no one can hit you"),
            new Help("gamemode", "/smp gamemode <mode> [player]", "Change game mode (survival, creative, adventure, spectator)"));

    private static final List<String> SUBS = HELP.stream().map(Help::sub).toList();

    /** Commands that every player can use. */
    private static final Set<String> PLAYER_SUBS = Set.of(
            "ping", "playtime", "sethome", "home", "delhome", "homes", "back", "night");

    /** Commands where the next word is a player name (used for tab completion). */
    private static final Set<String> PLAYER_ARG = Set.of(
            "ban", "mute", "jail", "unjail", "freeze", "unfreeze", "god", "feed", "heal", "fly",
            "invsee", "enderchest", "lightning", "nick", "ping", "kill", "playtime",
            "keep-inventory", "keep-xp", "vanish", "tp", "tphere");

    /** Chat commands that are blocked while a player is muted. */
    private static final Set<String> CHAT_COMMANDS = Set.of(
            "msg", "tell", "w", "whisper", "pm", "dm", "r", "reply", "me", "say", "t", "tm", "teammsg");

    private File dataFile;
    private Statistic playStat;

    private final Map<UUID, BanInfo> bans = new ConcurrentHashMap<>();
    private final Map<UUID, MuteInfo> mutes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> jailed = new HashMap<>();
    private final Map<UUID, Location> jailReturn = new HashMap<>();
    private final Map<UUID, String> nicks = new HashMap<>();
    private final Map<UUID, Map<String, Location>> homes = new HashMap<>();
    private final Set<UUID> frozen = new HashSet<>();
    private final Set<UUID> god = new HashSet<>();
    private final Set<UUID> keepInv = new HashSet<>();
    private final Set<UUID> keepXp = new HashSet<>();
    private final Set<UUID> nightVision = new HashSet<>();

    // Not saved: these reset when the server restarts.
    private final Set<UUID> vanished = new HashSet<>();
    private final Map<UUID, Location> lastLocation = new HashMap<>();

    private Location jailLocation;

    // ------------------------------------------------------------------
    // Startup / shutdown
    // ------------------------------------------------------------------

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        dataFile = new File(getDataFolder(), "data.yml");
        playStat = findPlayStat();
        loadData();

        PluginCommand command = getCommand("smp");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        getServer().getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(this, 20L, 20L);

        for (Player p : Bukkit.getOnlinePlayers()) {
            applyNick(p);
        }
        getLogger().info("SMP-X enabled. Type /smp in game to see all commands.");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    /** The playtime statistic has a different name on some server versions. */
    private Statistic findPlayStat() {
        for (String name : new String[]{"PLAY_ONE_MINUTE", "PLAY_TIME"}) {
            try {
                return Statistic.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                // Try the next name.
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Main command
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            sendHelp(s);
            return true;
        }

        String sub = normalize(a[0]);
        if (!SUBS.contains(sub)) {
            send(s, "&cUnknown command. Type &e/smp &cto see all commands.");
            return true;
        }

        if (!canUse(s, sub)) {
            send(s, switch (sub) {
                case "god" -> "&cOnly the server owner can use this command.";
                case "gamemode" -> "&cOnly OP players can use this command.";
                default -> "&cYou do not have permission to use this command.";
            });
            return true;
        }

        switch (sub) {
            case "ping" -> cmdPing(s, a);
            case "playtime" -> cmdPlaytime(s, a);
            case "sethome" -> cmdSetHome(s, a);
            case "home" -> cmdHome(s, a);
            case "delhome" -> cmdDelHome(s, a);
            case "homes" -> cmdHomes(s);
            case "back" -> cmdBack(s);
            case "night" -> cmdNight(s);
            case "ban" -> cmdBan(s, a);
            case "unban" -> cmdUnban(s, a);
            case "banlist" -> cmdBanList(s);
            case "mute" -> cmdMute(s, a);
            case "unmute" -> cmdUnmute(s, a);
            case "jail" -> cmdJail(s, a);
            case "unjail" -> cmdUnjail(s, a);
            case "setjail" -> cmdSetJail(s);
            case "freeze" -> cmdFreeze(s, a);
            case "unfreeze" -> cmdUnfreeze(s, a);
            case "vanish" -> cmdVanish(s, a);
            case "tp" -> cmdTp(s, a);
            case "tphere" -> cmdTpHere(s, a);
            case "feed" -> cmdFeed(s, a);
            case "heal" -> cmdHeal(s, a);
            case "fly" -> cmdFly(s, a);
            case "invsee" -> cmdInvsee(s, a);
            case "enderchest" -> cmdEnderChest(s, a);
            case "lightning" -> cmdLightning(s, a);
            case "nick" -> cmdNick(s, a);
            case "kill" -> cmdKill(s, a);
            case "keep-inventory" -> cmdKeep(s, a, keepInv, "keep inventory");
            case "keep-xp" -> cmdKeep(s, a, keepXp, "keep XP");
            case "god" -> cmdGod(s, a);
            case "gamemode" -> cmdGamemode(s, a);
            default -> sendHelp(s);
        }
        return true;
    }

    private String normalize(String raw) {
        String sub = raw.toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "ender-chest", "echest", "ec" -> "enderchest";
            case "keepinventory", "keepinv", "keep-inv" -> "keep-inventory";
            case "keepxp" -> "keep-xp";
            case "gm" -> "gamemode";
            case "teleport" -> "tp";
            case "bans", "ban-list" -> "banlist";
            case "v" -> "vanish";
            case "nightvision", "nv" -> "night";
            case "sh" -> "sethome";
            default -> sub;
        };
    }

    private boolean canUse(CommandSender s, String sub) {
        if (PLAYER_SUBS.contains(sub)) {
            return s.hasPermission("smpx.player");
        }
        if (sub.equals("god")) {
            return s.hasPermission("smpx.owner");
        }
        if (sub.equals("gamemode")) {
            return s.isOp();
        }
        return s.hasPermission("smpx.admin");
    }

    private String groupOf(String sub) {
        if (PLAYER_SUBS.contains(sub)) {
            return "&6&lPlayer commands";
        }
        if (sub.equals("god")) {
            return "&4&lOwner only";
        }
        if (sub.equals("gamemode")) {
            return "&4&lOP only";
        }
        return "&c&lAdmin commands";
    }

    private void sendHelp(CommandSender s) {
        send(s, "&b&lSMP-X &7- all commands you can use");
        String lastGroup = "";
        for (Help h : HELP) {
            if (!canUse(s, h.sub())) {
                continue;
            }
            String group = groupOf(h.sub());
            if (!group.equals(lastGroup)) {
                s.sendMessage(c(group));
                lastGroup = group;
            }
            s.sendMessage(c("&e" + h.usage() + " &8- &7" + h.description()));
        }
    }

    // ------------------------------------------------------------------
    // Ban / unban / banlist
    // ------------------------------------------------------------------

    private void cmdBan(CommandSender s, String[] a) {
        if (a.length < 2) {
            usage(s, "/smp ban <player> [time] [reason]",
                    "Time examples: 30s, 10m, 2h, 1d or perm (no time = permanent)",
                    "Example: /smp ban Steve 1d Hacking");
            return;
        }
        OfflinePlayer target = findPlayer(a[1]);
        if (target == null) {
            notFound(s, a[1]);
            return;
        }
        TimeArg time = readTime(a, 2);
        if (time == null) {
            send(s, "&cInvalid time. Use for example 30s, 10m, 2h, 1d or perm.");
            return;
        }
        String reason = a.length > time.next() ? join(a, time.next()) : NO_REASON;
        long until = time.seconds() == 0 ? PERMANENT : System.currentTimeMillis() + time.seconds() * 1000L;

        BanInfo ban = new BanInfo(until, reason, senderName(s));
        bans.put(target.getUniqueId(), ban);
        saveData();

        Player online = target.getPlayer();
        if (online != null) {
            online.kickPlayer(banScreen(ban));
        }
        send(s, "&e" + nameOf(target) + " &7has been banned "
                + (time.seconds() == 0 ? "&cpermanently&7" : "for &e" + formatTime(time.seconds()) + "&7")
                + ". Reason: &f" + reason);
    }

    private void cmdUnban(CommandSender s, String[] a) {
        if (a.length < 2) {
            usage(s, "/smp unban <player>");
            return;
        }
        UUID id = null;
        OfflinePlayer target = findPlayer(a[1]);
        if (target != null && bans.containsKey(target.getUniqueId())) {
            id = target.getUniqueId();
        } else {
            for (UUID banned : bans.keySet()) {
                String n = Bukkit.getOfflinePlayer(banned).getName();
                if (n != null && n.equalsIgnoreCase(a[1])) {
                    id = banned;
                    break;
                }
            }
        }
        if (id == null) {
            send(s, "&e" + a[1] + " &cis not banned.");
            return;
        }
        bans.remove(id);
        saveData();
        send(s, "&e" + a[1] + " &7has been unbanned.");
    }

    private void cmdBanList(CommandSender s) {
        if (bans.isEmpty()) {
            send(s, "&7No players are banned.");
            return;
        }
        send(s, "&b&lBanned players &7(" + bans.size() + ")");
        long now = System.currentTimeMillis();
        int shown = 0;
        for (Map.Entry<UUID, BanInfo> en : bans.entrySet()) {
            if (shown >= 30) {
                s.sendMessage(c("&7... and " + (bans.size() - shown) + " more."));
                break;
            }
            shown++;
            BanInfo b = en.getValue();
            String name = nameOf(Bukkit.getOfflinePlayer(en.getKey()));
            String left = b.until() == PERMANENT
                    ? "&cPermanent"
                    : "&e" + formatTime(Math.max(1, (b.until() - now) / 1000L)) + " left";
            s.sendMessage(c("&7- &f" + name + " &8| " + left + " &8| &7" + b.reason()
                    + " &8(by " + b.by() + ")"));
        }
    }

    private String banScreen(BanInfo b) {
        String left = b.until() == PERMANENT
                ? "&fPermanent"
                : "&f" + formatTime(Math.max(1, (b.until() - System.currentTimeMillis()) / 1000L));
        return c("&cYou are banned from this server.\n\n&7Reason: &f" + b.reason()
                + "\n&7Time left: " + left);
    }

    // ------------------------------------------------------------------
    // Mute / unmute
    // ------------------------------------------------------------------

    private void cmdMute(CommandSender s, String[] a) {
        if (a.length < 2) {
            usage(s, "/smp mute <player> [time] [reason]",
                    "Time examples: 30s, 10m, 2h, 1d or perm (no time = permanent)",
                    "Example: /smp mute Steve 10m Spamming");
            return;
        }
        OfflinePlayer target = findPlayer(a[1]);
        if (target == null) {
            notFound(s, a[1]);
            return;
        }
        TimeArg time = readTime(a, 2);
        if (time == null) {
            send(s, "&cInvalid time. Use for example 30s, 10m, 2h, 1d or perm.");
            return;
        }
        String reason = a.length > time.next() ? join(a, time.next()) : NO_REASON;
        long until = time.seconds() == 0 ? PERMANENT : System.currentTimeMillis() + time.seconds() * 1000L;

        mutes.put(target.getUniqueId(), new MuteInfo(until, reason, senderName(s)));
        saveData();

        String length = time.seconds() == 0 ? "permanently" : "for " + formatTime(time.seconds());
        Player online = target.getPlayer();
        if (online != null) {
            send(online, "&cYou have been muted " + length + ". Reason: &f" + reason);
        }
        send(s, "&e" + nameOf(target) + " &7has been muted " + length + ". Reason: &f" + reason);
    }

    private void cmdUnmute(CommandSender s, String[] a) {
        if (a.length < 2) {
            usage(s, "/smp unmute <player>");
            return;
        }
        OfflinePlayer target = findPlayer(a[1]);
        if (target == null || mutes.remove(target.getUniqueId()) == null) {
            send(s, "&e" + a[1] + " &cis not muted.");
            return;
        }
        saveData();
        Player online = target.getPlayer();
        if (online != null) {
            send(online, "&aYou have been unmuted. You can chat again.");
        }
        send(s, "&e" + nameOf(target) + " &7has been unmuted.");
    }

    private boolean isMuted(UUID id) {
        MuteInfo m = mutes.get(id);
        return m != null && (m.until() == PERMANENT || System.currentTimeMillis() < m.until());
    }

    private String muteText(UUID id) {
        MuteInfo m = mutes.get(id);
        if (m == null) {
            return "";
        }
        String left = m.until() == PERMANENT
                ? "permanent"
                : formatTime(Math.max(1, (m.until() - System.currentTimeMillis()) / 1000L)) + " left";
        return "&cYou are muted. &7Reason: &f" + m.reason() + " &7(" + left + ")";
    }

    // ------------------------------------------------------------------
    // Jail / unjail
    // ------------------------------------------------------------------

    private void cmdSetJail(CommandSender s) {
        Player p = self(s);
        if (p == null) {
            return;
        }
        jailLocation = p.getLocation();
        saveData();
        send(s, "&aThe jail has been set at your location.");
    }

    private void cmdJail(CommandSender s, String[] a) {
        if (a.length < 2) {
            usage(s, "/smp jail <player> [time]", "Time examples: 30s, 10m, 2h, 1d or perm");
            return;
        }
        if (jailLocation == null) {
            send(s, "&cThe jail is not set yet. Stand where you want it and type &e/smp setjail&c.");
            return;
        }
        Player target = online(s, a[1]);
        if (target == null) {
            return;
        }
        long seconds = 0;
        if (a.length >= 3) {
            seconds = parseTime(a[2]);
            if (seconds < 0) {
                send(s, "&cInvalid time. Use for example 30s, 10m, 2h, 1d or perm.");
                return;
            }
        }
        UUID id = target.getUniqueId();
        if (!jailed.containsKey(id)) {
            jailReturn.put(id, target.getLocation());
        }
        jailed.put(id, seconds == 0 ? PERMANENT : System.currentTimeMillis() + seconds * 1000L);
        saveData();
        target.teleport(jailLocation);

        String length = seconds == 0 ? "until an admin releases you" : "for " + formatTime(seconds);
        send(target, "&cYou have been jailed " + length + ".");
        send(s, "&e" + target.getName() + " &7has been jailed"
                + (seconds == 0 ? "." : " for &e" + formatTime(seconds) + "&7."));
    }

    private void cmdUnjail(CommandSender s, String[] a) {
        if (a.length < 2) {
            usage(s, "/smp unjail <player>");
            return;
        }
        OfflinePlayer target = findPlayer(a[1]);
        if (target == null || !jailed.containsKey(target.getUniqueId())) {
            send(s, "&e" + a[1] + " &cis not in jail.");
            return;
        }
        release(target.getUniqueId());
        send(s, "&e" + nameOf(target) + " &7has been released from jail.");
    }

    private void release(UUID id) {
        jailed.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            Location back = jailReturn.remove(id);
            p.teleport(back != null ? back : p.getWorld().getSpawnLocation());
            send(p, "&aYou have been released from jail.");
        }
        saveData();
    }

    // ------------------------------------------------------------------
    // Freeze / unfreeze
    // ------------------------------------------------------------------

    private void cmdFreeze(CommandSender s, String[] a) {
        if (a.length < 2) {
            usage(s, "/smp freeze <player>");
            return;
        }
        Player target = online(s, a[1]);
        if (target == null) {
            return;
        }
        frozen.add(target.getUniqueId());
        saveData();
        send(target, "&bYou have been frozen. You cannot move.");
        send(s, "&e" + target.getName() + " &7is now frozen.");
    }

    private void cmdUnfreeze(CommandSender s, String[] a) {
        if (a.length < 2) {
            usage(s, "/smp unfreeze <player>");
            return;
        }
        OfflinePlayer target = findPlayer(a[1]);
        if (target == null || !frozen.contains(target.getUniqueId())) {
            send(s, "&e" + a[1] + " &cis not frozen.");
            return;
        }
        frozen.remove(target.getUniqueId());
        saveData();
        Player online = target.getPlayer();
        if (online != null) {
            send(online, "&aYou have been unfrozen. You can move again.");
        }
        send(s, "&e" + nameOf(target) + " &7is no longer frozen.");
    }

    // ------------------------------------------------------------------
    // Vanish
    // ------------------------------------------------------------------

    private void cmdVanish(CommandSender s, String[] a) {
        Player target = targetOrSelf(s, a);
        if (target == null) {
            return;
        }
        UUID id = target.getUniqueId();
        boolean on = vanished.add(id);
        if (!on) {
            vanished.remove(id);
        }
        applyVanish(target, on);
        send(target, on ? "&aVanish enabled. Normal players cannot see you."
                : "&cVanish disabled. Everyone can see you.");
        if (!target.equals(s)) {
            send(s, "&7Vanish for &e" + target.getName() + "&7: " + (on ? "&aON" : "&cOFF"));
        }
    }

    private void applyVanish(Player p, boolean on) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p)) {
                continue;
            }
            if (on && !other.hasPermission("smpx.admin")) {
                other.hidePlayer(this, p);
            } else {
                other.showPlayer(this, p);
            }
        }
        p.setSleepingIgnored(on);
        p.setCollidable(!on);
    }

    // ------------------------------------------------------------------
    // Teleport
    // ------------------------------------------------------------------

    private void cmdTp(CommandSender s, String[] a) {
        if (a.length < 2) {
            usage(s, "/smp tp <player>", "/smp tp <player> <toPlayer>");
            return;
        }
        if (a.length >= 3) {
            Player who = online(s, a[1]);
            if (who == null) {
                return;
            }
            Player to = online(s, a[2]);
            if (to == null) {
                return;
            }
            teleportTo(who, to.getLocation());
            send(s, "&7Teleported &e" + who.getName() + " &7to &e" + to.getName() + "&7.");
            return;
        }
        Player me = self(s);
        if (me == null) {
            return;
        }
        Player to = online(s, a[1]);
        if (to == null) {
            return;
        }
        teleportTo(me, to.getLocation());
        send(s, "&7Teleported to &e" + to.getName() + "&7.");
    }

    private void cmdTpHere(CommandSender s, String[] a) {
        if (a.length < 2) {
            usage(s, "/smp tphere <player>");
            return;
        }
        Player me = self(s);
        if (me == null) {
            return;
        }
        Player target = online(s, a[1]);
        if (target == null) {
            return;
        }
        teleportTo(target, me.getLocation());
        send(target, "&7You were teleported to &e" + me.getName() + "&7.");
        send(s, "&7Teleported &e" + target.getName() + " &7to you.");
    }

    /** Teleports a player and remembers where they were, for /smp back. */
    private void teleportTo(Player p, Location destination) {
        lastLocation.put(p.getUniqueId(), p.getLocation());
        p.teleport(destination);
    }

    // ------------------------------------------------------------------
    // Game mode (OP only)
    // ------------------------------------------------------------------

    private void cmdGamemode(CommandSender s, String[] a) {
        if (a.length < 2) {
            usage(s, "/smp gamemode <survival|creative|adventure|spectator> [player]");
            return;
        }
        GameMode mode = parseMode(a[1]);
        if (mode == null) {
            send(s, "&cUnknown game mode. Use survival, creative, adventure or spectator.");
            return;
        }
        Player target = targetAt(s, a, 2);
        if (target == null) {
            return;
        }
        target.setGameMode(mode);
        String name = mode.name().toLowerCase(Locale.ROOT);
        send(target, "&7Your game mode is now &e" + name + "&7.");
        if (!target.equals(s)) {
            send(s, "&7Game mode of &e" + target.getName() + " &7is now &e" + name + "&7.");
        }
    }

    private GameMode parseMode(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "survival", "s", "0" -> GameMode.SURVIVAL;
            case "creative", "c", "1" -> GameMode.CREATIVE;
            case "adventure", "a", "2" -> GameMode.ADVENTURE;
            case "spectator", "sp", "3" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    // ------------------------------------------------------------------
    // Homes and back
    // ------------------------------------------------------------------

    private void cmdSetHome(CommandSender s, String[] a) {
        Player p = self(s);
        if (p == null) {
            return;
        }
        String name = a.length >= 2 ? a[1].toLowerCase(Locale.ROOT) : "home";
        if (!name.matches("[a-z0-9_-]{1,16}")) {
            send(s, "&cA home name can only use letters, numbers, - and _ (up to 16 characters).");
            return;
        }
        Map<String, Location> mine = homes.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashMap<>());
        if (!mine.containsKey(name) && mine.size() >= MAX_HOMES && !p.hasPermission("smpx.admin")) {
            send(s, "&cYou can only have &e" + MAX_HOMES + " &chomes. Delete one with &e/smp delhome <name>&c.");
            return;
        }
        mine.put(name, p.getLocation());
        saveData();
        send(s, "&aHome &e" + name + " &ahas been saved. Use &e/smp home " + name + " &ato come back.");
    }

    private void cmdHome(CommandSender s, String[] a) {
        Player p = self(s);
        if (p == null || cannotTeleport(p)) {
            return;
        }
        Map<String, Location> mine = homes.getOrDefault(p.getUniqueId(), Map.of());
        if (mine.isEmpty()) {
            send(s, "&cYou have no home yet. Type &e/smp sethome &cto save one.");
            return;
        }
        String name;
        if (a.length >= 2) {
            name = a[1].toLowerCase(Locale.ROOT);
        } else if (mine.containsKey("home")) {
            name = "home";
        } else if (mine.size() == 1) {
            name = mine.keySet().iterator().next();
        } else {
            send(s, "&cYou have more than one home. Choose one: &e" + String.join("&7, &e", mine.keySet()));
            return;
        }
        Location loc = mine.get(name);
        if (loc == null) {
            send(s, "&cYou have no home named &e" + name + "&c. Your homes: &e"
                    + String.join("&7, &e", mine.keySet()));
            return;
        }
        if (!loc.isWorldLoaded()) {
            send(s, "&cThe world of that home is not loaded.");
            return;
        }
        teleportTo(p, loc);
        send(s, "&aTeleported to home &e" + name + "&a.");
    }

    private void cmdDelHome(CommandSender s, String[] a) {
        Player p = self(s);
        if (p == null) {
            return;
        }
        Map<String, Location> mine = homes.getOrDefault(p.getUniqueId(), Map.of());
        String name = a.length >= 2 ? a[1].toLowerCase(Locale.ROOT) : "home";
        if (!mine.containsKey(name)) {
            send(s, "&cYou have no home named &e" + name + "&c.");
            return;
        }
        mine.remove(name);
        saveData();
        send(s, "&7Home &e" + name + " &7has been deleted.");
    }

    private void cmdHomes(CommandSender s) {
        Player p = self(s);
        if (p == null) {
            return;
        }
        Map<String, Location> mine = homes.getOrDefault(p.getUniqueId(), Map.of());
        if (mine.isEmpty()) {
            send(s, "&7You have no homes. Type &e/smp sethome &7to save one.");
            return;
        }
        send(s, "&7Your homes (&e" + mine.size() + "&7/" + MAX_HOMES + "): &e"
                + String.join("&7, &e", mine.keySet()));
    }

    private void cmdBack(CommandSender s) {
        Player p = self(s);
        if (p == null || cannotTeleport(p)) {
            return;
        }
        Location back = lastLocation.get(p.getUniqueId());
        if (back == null) {
            send(s, "&cYou have no previous location to go back to.");
            return;
        }
        teleportTo(p, back);
        send(s, "&aTeleported back to your previous location.");
    }

    private boolean cannotTeleport(Player p) {
        if (jailed.containsKey(p.getUniqueId())) {
            send(p, "&cYou cannot teleport while you are in jail.");
            return true;
        }
        if (frozen.contains(p.getUniqueId())) {
            send(p, "&cYou cannot teleport while you are frozen.");
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Night vision
    // ------------------------------------------------------------------

    private void cmdNight(CommandSender s) {
        Player p = self(s);
        if (p == null) {
            return;
        }
        UUID id = p.getUniqueId();
        if (nightVision.add(id)) {
            giveNightVision(p);
            saveData();
            send(s, "&aNight vision is on. Type &e/smp night &aagain to turn it off.");
        } else {
            nightVision.remove(id);
            p.removePotionEffect(PotionEffectType.NIGHT_VISION);
            saveData();
            send(s, "&cNight vision is off.");
        }
    }

    private void giveNightVision(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                PotionEffect.INFINITE_DURATION, 0, false, false, true));
    }

    // ------------------------------------------------------------------
    // God / feed / heal / fly
    // ------------------------------------------------------------------

    private void cmdGod(CommandSender s, String[] a) {
        Player target = targetOrSelf(s, a);
        if (target == null) {
            return;
        }
        boolean on = toggle(god, target.getUniqueId());
        send(target, on ? "&aGod mode enabled. No one can hit you." : "&cGod mode disabled.");
        if (!target.equals(s)) {
            send(s, "&7God mode for &e" + target.getName() + "&7: " + (on ? "&aON" : "&cOFF"));
        }
    }

    private void cmdFeed(CommandSender s, String[] a) {
        Player target = targetOrSelf(s, a);
        if (target == null) {
            return;
        }
        target.setFoodLevel(20);
        target.setSaturation(20f);
        send(target, "&aYour hunger has been filled.");
        if (!target.equals(s)) {
            send(s, "&7You fed &e" + target.getName() + "&7.");
        }
    }

    private void cmdHeal(CommandSender s, String[] a) {
        Player target = targetOrSelf(s, a);
        if (target == null) {
            return;
        }
        target.setHealth(target.getMaxHealth());
        target.setFireTicks(0);
        send(target, "&aYou have been healed.");
        if (!target.equals(s)) {
            send(s, "&7You healed &e" + target.getName() + "&7.");
        }
    }

    private void cmdFly(CommandSender s, String[] a) {
        Player target = targetOrSelf(s, a);
        if (target == null) {
            return;
        }
        boolean enable = !target.getAllowFlight();
        target.setAllowFlight(enable);
        if (!enable) {
            target.setFlying(false);
        }
        send(target, enable ? "&aFlight enabled." : "&cFlight disabled.");
        if (!target.equals(s)) {
            send(s, "&7Flight for &e" + target.getName() + "&7: " + (enable ? "&aON" : "&cOFF"));
        }
    }

    // ------------------------------------------------------------------
    // Invsee / ender chest
    // ------------------------------------------------------------------

    private void cmdInvsee(CommandSender s, String[] a) {
        if (!(s instanceof Player viewer)) {
            send(s, "&cOnly players can open inventories.");
            return;
        }
        if (a.length < 2) {
            usage(s, "/smp invsee <player>");
            return;
        }
        Player target = online(s, a[1]);
        if (target == null) {
            return;
        }
        viewer.openInventory(target.getInventory());
        send(s, "&7Opened the inventory of &e" + target.getName() + "&7.");
    }

    private void cmdEnderChest(CommandSender s, String[] a) {
        if (!(s instanceof Player viewer)) {
            send(s, "&cOnly players can open ender chests.");
            return;
        }
        if (a.length < 2) {
            usage(s, "/smp enderchest <player>");
            return;
        }
        Player target = online(s, a[1]);
        if (target == null) {
            return;
        }
        viewer.openInventory(target.getEnderChest());
        send(s, "&7Opened the ender chest of &e" + target.getName() + "&7.");
    }

    // ------------------------------------------------------------------
    // Lightning / kill
    // ------------------------------------------------------------------

    private void cmdLightning(CommandSender s, String[] a) {
        if (a.length < 2) {
            usage(s, "/smp lightning <player>");
            return;
        }
        Player target = online(s, a[1]);
        if (target == null) {
            return;
        }
        // Exactly one lightning bolt.
        target.getWorld().strikeLightning(target.getLocation());
        send(s, "&7Lightning struck &e" + target.getName() + "&7.");
    }

    private void cmdKill(CommandSender s, String[] a) {
        if (a.length < 2) {
            usage(s, "/smp kill <player>");
            return;
        }
        Player target = online(s, a[1]);
        if (target == null) {
            return;
        }
        target.setHealth(0.0);
        send(s, "&e" + target.getName() + " &7has been killed.");
    }

    // ------------------------------------------------------------------
    // Nick
    // ------------------------------------------------------------------

    private void cmdNick(CommandSender s, String[] a) {
        if (a.length < 3) {
            usage(s, "/smp nick <realplayername> <newname|reset>", "You can use colour codes like &a or &6");
            return;
        }
        OfflinePlayer target = findPlayer(a[1]);
        if (target == null) {
            notFound(s, a[1]);
            return;
        }
        String newName = join(a, 2);
        UUID id = target.getUniqueId();

        if (newName.equalsIgnoreCase("reset") || newName.equalsIgnoreCase("off")) {
            nicks.remove(id);
            send(s, "&7The nickname of &e" + nameOf(target) + " &7has been reset.");
        } else {
            nicks.put(id, newName);
            send(s, "&7The nickname of &e" + nameOf(target) + " &7is now " + c(newName) + "&7.");
        }
        saveData();

        Player online = target.getPlayer();
        if (online != null) {
            applyNick(online);
        }
    }

    private void applyNick(Player p) {
        String nick = nicks.get(p.getUniqueId());
        String shown = nick == null ? p.getName() : c(nick + "&r");
        p.setDisplayName(shown);
        p.setPlayerListName(shown);
    }

    // ------------------------------------------------------------------
    // Ping (everyone can use it)
    // ------------------------------------------------------------------

    private void cmdPing(CommandSender s, String[] a) {
        if (a.length >= 2) {
            Player target = online(s, a[1]);
            if (target == null) {
                return;
            }
            send(s, "&e" + target.getName() + "&7's ping: " + pingText(target.getPing()));
            return;
        }
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            send(s, "&cNo players are online.");
            return;
        }
        send(s, "&b&lPlayer pings &7(&agreen = low&7, &eyellow = medium&7, &cred = high&7)");
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (vanished.contains(p.getUniqueId()) && !s.hasPermission("smpx.admin")) {
                continue;
            }
            send(s, "&7- &f" + p.getName() + "&7: " + pingText(p.getPing()));
        }
    }

    private String pingText(int ping) {
        String color = ping < 100 ? "&a" : (ping < 200 ? "&e" : "&c");
        return color + ping + "ms";
    }

    // ------------------------------------------------------------------
    // Playtime (everyone can use it)
    // ------------------------------------------------------------------

    private void cmdPlaytime(CommandSender s, String[] a) {
        if (a.length >= 2) {
            OfflinePlayer target = findPlayer(a[1]);
            if (target == null) {
                notFound(s, a[1]);
                return;
            }
            send(s, "&e" + nameOf(target) + " &7has played: &a" + formatTime(playSeconds(target)));
            return;
        }

        List<OfflinePlayer> players = new ArrayList<>();
        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
            if (p.getName() != null && (p.hasPlayedBefore() || p.isOnline())) {
                players.add(p);
            }
        }
        if (players.isEmpty()) {
            send(s, "&cNo playtime has been recorded yet.");
            return;
        }
        players.sort(Comparator.comparingLong(this::playSeconds).reversed());

        send(s, "&b&lPlaytime of all players");
        int limit = 30;
        int shown = 0;
        for (OfflinePlayer p : players) {
            if (shown >= limit) {
                break;
            }
            shown++;
            send(s, "&e" + shown + ". &f" + p.getName() + " &7- &a" + formatTime(playSeconds(p)));
        }
        if (players.size() > limit) {
            send(s, "&7... and " + (players.size() - limit) + " more players.");
        }
    }

    private long playSeconds(OfflinePlayer p) {
        return playStat == null ? 0L : p.getStatistic(playStat) / 20L;
    }

    // ------------------------------------------------------------------
    // Keep inventory / keep XP (per player)
    // ------------------------------------------------------------------

    private void cmdKeep(CommandSender s, String[] a, Set<UUID> set, String what) {
        if (a.length < 2) {
            usage(s, "/smp " + a[0].toLowerCase(Locale.ROOT) + " <player>");
            return;
        }
        OfflinePlayer target = findPlayer(a[1]);
        if (target == null) {
            notFound(s, a[1]);
            return;
        }
        boolean on = toggle(set, target.getUniqueId());
        send(s, "&e" + nameOf(target) + " &7will " + (on ? "&anow" : "&cno longer")
                + " &7" + what + " when they die.");
        Player online = target.getPlayer();
        if (online != null) {
            send(online, on ? "&aYou will now " + what + " when you die."
                    : "&cYou will no longer " + what + " when you die.");
        }
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        BanInfo ban = bans.get(e.getUniqueId());
        if (ban == null) {
            return;
        }
        if (ban.until() != PERMANENT && System.currentTimeMillis() >= ban.until()) {
            return; // The ban has expired. The timer removes it shortly.
        }
        e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banScreen(ban));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        applyNick(p);

        // Hide vanished players from the new player, unless they are an admin.
        if (!p.hasPermission("smpx.admin")) {
            for (UUID hiddenId : vanished) {
                Player hidden = Bukkit.getPlayer(hiddenId);
                if (hidden != null) {
                    p.hidePlayer(this, hidden);
                }
            }
        }
        // If the new player is vanished, hide them and their join message.
        if (vanished.contains(id)) {
            applyVanish(p, true);
            e.setJoinMessage(null);
        }
        if (nightVision.contains(id)) {
            giveNightVision(p);
        }

        Bukkit.getScheduler().runTask(this, () -> {
            if (!p.isOnline()) {
                return;
            }
            Long until = jailed.get(id);
            if (until != null) {
                if (until != PERMANENT && System.currentTimeMillis() >= until) {
                    release(id);
                } else if (jailLocation != null) {
                    p.teleport(jailLocation);
                }
            } else if (jailReturn.containsKey(id)) {
                // Was released while offline - send them back to where they were.
                Location back = jailReturn.remove(id);
                saveData();
                if (back != null) {
                    p.teleport(back);
                }
                send(p, "&aYou have been released from jail.");
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (vanished.contains(e.getPlayer().getUniqueId())) {
            e.setQuitMessage(null);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to == null) {
            return;
        }
        UUID id = e.getPlayer().getUniqueId();

        if (jailed.containsKey(id) && jailLocation != null) {
            if (to.getWorld() != jailLocation.getWorld() || to.distanceSquared(jailLocation) > 100) {
                e.setTo(jailLocation);
            }
            return;
        }

        if (frozen.contains(id)) {
            Location from = e.getFrom();
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                Location stay = from.clone();
                stay.setYaw(to.getYaw());
                stay.setPitch(to.getPitch());
                e.setTo(stay);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (isMuted(id)) {
            e.setCancelled(true);
            send(e.getPlayer(), muteText(id));
        }
    }

    @EventHandler
    public void onCommandUse(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (jailed.containsKey(p.getUniqueId()) && !p.hasPermission("smpx.admin")) {
            e.setCancelled(true);
            send(p, "&cYou cannot use commands while you are in jail.");
            return;
        }
        if (isMuted(p.getUniqueId())) {
            String first = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
            int colon = first.indexOf(':');
            if (colon >= 0) {
                first = first.substring(colon + 1);
            }
            if (CHAT_COMMANDS.contains(first)) {
                e.setCancelled(true);
                send(p, muteText(p.getUniqueId()));
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && god.contains(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        UUID id = p.getUniqueId();

        // So the player can use /smp back to return to where they died.
        lastLocation.put(id, p.getLocation());

        if (keepInv.contains(id)) {
            e.setKeepInventory(true);
            e.getDrops().clear();
        }
        if (keepXp.contains(id)) {
            e.setKeepLevel(true);
            e.setDroppedExp(0);
        }
    }

    // ------------------------------------------------------------------
    // Timer: expire bans, mutes and jail time, keep night vision on
    // ------------------------------------------------------------------

    private void tick() {
        long now = System.currentTimeMillis();

        boolean changed = false;
        Iterator<Map.Entry<UUID, BanInfo>> banIt = bans.entrySet().iterator();
        while (banIt.hasNext()) {
            long until = banIt.next().getValue().until();
            if (until != PERMANENT && now >= until) {
                banIt.remove();
                changed = true;
            }
        }
        Iterator<Map.Entry<UUID, MuteInfo>> muteIt = mutes.entrySet().iterator();
        while (muteIt.hasNext()) {
            Map.Entry<UUID, MuteInfo> en = muteIt.next();
            long until = en.getValue().until();
            if (until != PERMANENT && now >= until) {
                muteIt.remove();
                changed = true;
                Player p = Bukkit.getPlayer(en.getKey());
                if (p != null) {
                    send(p, "&aYour mute has ended. You can chat again.");
                }
            }
        }
        if (changed) {
            saveData();
        }

        for (UUID id : new ArrayList<>(jailed.keySet())) {
            long until = jailed.get(id);
            if (until != PERMANENT && now >= until) {
                release(id);
            }
        }

        // Night vision is removed by death or milk, so give it back.
        for (UUID id : nightVision) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !p.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                giveNightVision(p);
            }
        }
    }

    // ------------------------------------------------------------------
    // Tab completion
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();

        if (a.length == 1) {
            for (String sub : SUBS) {
                if (canUse(s, sub)) {
                    out.add(sub);
                }
            }
        } else if (a.length == 2) {
            String sub = normalize(a[0]);
            if (!SUBS.contains(sub) || !canUse(s, sub)) {
                return out;
            }
            if (sub.equals("unban")) {
                for (UUID id : bans.keySet()) {
                    addName(out, Bukkit.getOfflinePlayer(id).getName());
                }
            } else if (sub.equals("unmute")) {
                for (UUID id : mutes.keySet()) {
                    addName(out, Bukkit.getOfflinePlayer(id).getName());
                }
            } else if (sub.equals("gamemode")) {
                out.addAll(List.of("survival", "creative", "adventure", "spectator"));
            } else if (sub.equals("home") || sub.equals("delhome")) {
                if (s instanceof Player p) {
                    out.addAll(homes.getOrDefault(p.getUniqueId(), Map.of()).keySet());
                }
            } else if (PLAYER_ARG.contains(sub)) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    out.add(p.getName());
                }
            }
        } else if (a.length == 3) {
            String sub = normalize(a[0]);
            if (!SUBS.contains(sub) || !canUse(s, sub)) {
                return out;
            }
            if (sub.equals("ban") || sub.equals("mute") || sub.equals("jail")) {
                out.addAll(List.of("30s", "10m", "1h", "1d", "perm"));
            } else if (sub.equals("nick")) {
                out.add("reset");
            } else if (sub.equals("tp") || sub.equals("gamemode")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    out.add(p.getName());
                }
            }
        }

        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    private void addName(List<String> list, String name) {
        if (name != null) {
            list.add(name);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String c(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private void send(CommandSender s, String text) {
        s.sendMessage(c(PREFIX + text));
    }

    private void usage(CommandSender s, String... lines) {
        send(s, "&cUsage: &e" + lines[0]);
        for (int i = 1; i < lines.length; i++) {
            s.sendMessage(c("&7" + lines[i]));
        }
    }

    private void notFound(CommandSender s, String name) {
        send(s, "&cPlayer &e" + name + " &cwas not found. They must have joined the server before.");
    }

    private String join(String[] a, int from) {
        return String.join(" ", Arrays.copyOfRange(a, from, a.length));
    }

    private String senderName(CommandSender s) {
        return s instanceof Player ? s.getName() : "Console";
    }

    /** The sender as a player, or an error message if the sender is the console. */
    private Player self(CommandSender s) {
        if (s instanceof Player p) {
            return p;
        }
        send(s, "&cOnly players can use this command.");
        return null;
    }

    private Player online(CommandSender s, String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p == null) {
            send(s, "&cPlayer &e" + name + " &cis not online.");
        }
        return p;
    }

    /** The player named at a[index], or the sender if no name was typed. */
    private Player targetAt(CommandSender s, String[] a, int index) {
        if (a.length > index) {
            return online(s, a[index]);
        }
        if (s instanceof Player p) {
            return p;
        }
        send(s, "&cThe console must give a player name.");
        return null;
    }

    private Player targetOrSelf(CommandSender s, String[] a) {
        return targetAt(s, a, 1);
    }

    private OfflinePlayer findPlayer(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) {
            return p;
        }
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    private String nameOf(OfflinePlayer p) {
        return p.getName() != null ? p.getName() : p.getUniqueId().toString();
    }

    private boolean toggle(Set<UUID> set, UUID id) {
        boolean added = set.add(id);
        if (!added) {
            set.remove(id);
        }
        saveData();
        return added;
    }

    /**
     * Reads an optional time at a[index].
     * Returns null if it looks like a time but is wrong (for example "10x").
     * If a[index] is not a time at all, there is no time and the rest is the reason.
     */
    private TimeArg readTime(String[] a, int index) {
        if (a.length <= index) {
            return new TimeArg(0, index);
        }
        long parsed = parseTime(a[index]);
        if (parsed >= 0) {
            return new TimeArg(parsed, index + 1);
        }
        if (a[index].matches("-?\\d+[a-zA-Z]?")) {
            return null;
        }
        return new TimeArg(0, index);
    }

    /** Returns seconds, 0 for permanent, or -1 if the text is not valid. */
    private long parseTime(String input) {
        String t = input.toLowerCase(Locale.ROOT).trim();
        if (t.equals("perm") || t.equals("permanent") || t.equals("forever")) {
            return 0;
        }
        if (t.isEmpty()) {
            return -1;
        }
        long multiplier = 1;
        char last = t.charAt(t.length() - 1);
        if (Character.isLetter(last)) {
            switch (last) {
                case 's' -> multiplier = 1;
                case 'm' -> multiplier = 60;
                case 'h' -> multiplier = 3600;
                case 'd' -> multiplier = 86400;
                case 'w' -> multiplier = 604800;
                default -> {
                    return -1;
                }
            }
            t = t.substring(0, t.length() - 1);
        }
        try {
            long n = Long.parseLong(t);
            return n <= 0 ? -1 : n * multiplier;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private String formatTime(long totalSeconds) {
        long d = totalSeconds / 86400;
        long h = (totalSeconds % 86400) / 3600;
        long m = (totalSeconds % 3600) / 60;
        long sec = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (sec > 0 || sb.length() == 0) sb.append(sec).append("s");
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------
    // Saving and loading (plugins/SMP-X/data.yml)
    // ------------------------------------------------------------------

    private synchronized void saveData() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("frozen", uuidList(frozen));
        y.set("god", uuidList(god));
        y.set("keep-inventory", uuidList(keepInv));
        y.set("keep-xp", uuidList(keepXp));
        y.set("night-vision", uuidList(nightVision));
        for (Map.Entry<UUID, BanInfo> en : bans.entrySet()) {
            String path = "bans." + en.getKey();
            y.set(path + ".until", en.getValue().until());
            y.set(path + ".reason", en.getValue().reason());
            y.set(path + ".by", en.getValue().by());
        }
        for (Map.Entry<UUID, MuteInfo> en : mutes.entrySet()) {
            String path = "mutes." + en.getKey();
            y.set(path + ".until", en.getValue().until());
            y.set(path + ".reason", en.getValue().reason());
            y.set(path + ".by", en.getValue().by());
        }
        for (Map.Entry<UUID, Long> en : jailed.entrySet()) {
            y.set("jailed." + en.getKey(), en.getValue());
        }
        for (Map.Entry<UUID, Location> en : jailReturn.entrySet()) {
            y.set("jail-return." + en.getKey(), en.getValue());
        }
        for (Map.Entry<UUID, String> en : nicks.entrySet()) {
            y.set("nicks." + en.getKey(), en.getValue());
        }
        for (Map.Entry<UUID, Map<String, Location>> en : homes.entrySet()) {
            for (Map.Entry<String, Location> home : en.getValue().entrySet()) {
                y.set("homes." + en.getKey() + "." + home.getKey(), home.getValue());
            }
        }
        y.set("jail-location", jailLocation);
        try {
            y.save(dataFile);
        } catch (IOException ex) {
            getLogger().warning("Could not save data.yml: " + ex.getMessage());
        }
    }

    private void loadData() {
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        readUuids(y.getStringList("frozen"), frozen);
        readUuids(y.getStringList("god"), god);
        readUuids(y.getStringList("keep-inventory"), keepInv);
        readUuids(y.getStringList("keep-xp"), keepXp);
        readUuids(y.getStringList("night-vision"), nightVision);

        ConfigurationSection sec = y.getConfigurationSection("bans");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                UUID id = parseUuid(key);
                if (id == null) {
                    continue;
                }
                ConfigurationSection b = sec.getConfigurationSection(key);
                if (b != null) {
                    bans.put(id, new BanInfo(b.getLong("until"), b.getString("reason", NO_REASON),
                            b.getString("by", "Console")));
                } else {
                    // Older save format: only the end time was stored.
                    bans.put(id, new BanInfo(sec.getLong(key), NO_REASON, "Console"));
                }
            }
        }
        sec = y.getConfigurationSection("mutes");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                UUID id = parseUuid(key);
                ConfigurationSection m = sec.getConfigurationSection(key);
                if (id != null && m != null) {
                    mutes.put(id, new MuteInfo(m.getLong("until"), m.getString("reason", NO_REASON),
                            m.getString("by", "Console")));
                }
            }
        }
        sec = y.getConfigurationSection("jailed");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                UUID id = parseUuid(key);
                if (id != null) jailed.put(id, sec.getLong(key));
            }
        }
        sec = y.getConfigurationSection("jail-return");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                UUID id = parseUuid(key);
                Location loc = sec.getLocation(key);
                if (id != null && loc != null) jailReturn.put(id, loc);
            }
        }
        sec = y.getConfigurationSection("nicks");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                UUID id = parseUuid(key);
                String nick = sec.getString(key);
                if (id != null && nick != null) nicks.put(id, nick);
            }
        }
        sec = y.getConfigurationSection("homes");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                UUID id = parseUuid(key);
                ConfigurationSection mine = sec.getConfigurationSection(key);
                if (id == null || mine == null) {
                    continue;
                }
                Map<String, Location> map = new LinkedHashMap<>();
                for (String name : mine.getKeys(false)) {
                    Location loc = mine.getLocation(name);
                    if (loc != null) {
                        map.put(name, loc);
                    }
                }
                if (!map.isEmpty()) {
                    homes.put(id, map);
                }
            }
        }
        jailLocation = y.getLocation("jail-location");
    }

    private List<String> uuidList(Set<UUID> set) {
        List<String> list = new ArrayList<>();
        for (UUID id : set) {
            list.add(id.toString());
        }
        return list;
    }

    private void readUuids(List<String> source, Set<UUID> target) {
        for (String text : source) {
            UUID id = parseUuid(text);
            if (id != null) {
                target.add(id);
            }
        }
    }

    private UUID parseUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
