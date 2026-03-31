package com.example.randomeffects;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

// ─────────────────────────────────────────────────────────────────────────────
//  RandomEffectsPlugin — single-file build
//  Drop RandomEffectsPlugin.jar into /plugins and restart.
// ─────────────────────────────────────────────────────────────────────────────
public class RandomEffectsPlugin extends JavaPlugin {

    private EffectManager effectManager;

    @Override
    public void onEnable() {
        effectManager = new EffectManager(this);
        getServer().getPluginManager().registerEvents(new Events(this, effectManager), this);

        CommandHandler cmd = new CommandHandler(this, effectManager);
        getCommand("rollroles").setExecutor(cmd);
        getCommand("rollrole").setExecutor(cmd);
        getCommand("rerollrole").setExecutor(cmd);
        getCommand("checkrole").setExecutor(cmd);

        getLogger().info("RandomEffectsPlugin enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("RandomEffectsPlugin disabled!");
    }

    // =========================================================================
    //  EFFECT MANAGER
    // =========================================================================
    public static class EffectManager {

        private final RandomEffectsPlugin plugin;
        private final Map<UUID, PlayerState> states = new HashMap<>();
        private boolean globalRollDone = false;

        private static final int PERMANENT = Integer.MAX_VALUE;

        public enum Role { STRENGTH, SPEED, REGENERATION, HASTE, RESISTANCE }

        public static class PlayerState {
            public Role role;
            public int killStage  = 0; // 0 = base, 1 = lvl2, 2 = bonus unlocked
            public int deathStage = 0; // 0 = no punishment, 1-3 = punishment levels
            public PlayerState(Role role) { this.role = role; }
        }

        public EffectManager(RandomEffectsPlugin plugin) { this.plugin = plugin; }

        // ── Public API ────────────────────────────────────────────────────────

        public boolean isGlobalRollDone() { return globalRollDone; }
        public void setGlobalRollDone(boolean v) { globalRollDone = v; }
        public boolean hasRole(Player p) { return states.containsKey(p.getUniqueId()); }
        public PlayerState getState(UUID uuid) { return states.get(uuid); }
        public void removePlayer(UUID uuid) { states.remove(uuid); }

        public void assignRandomRole(Player player) {
            Role[] roles = Role.values();
            Role chosen = roles[new Random().nextInt(roles.length)];
            PlayerState state = new PlayerState(chosen);
            states.put(player.getUniqueId(), state);
            applyEffects(player, state);
            player.sendMessage(ChatColor.GOLD + "[RandomEffects] " + ChatColor.YELLOW
                    + "You have been assigned: " + ChatColor.AQUA + formatRole(state));
        }

        public void onKill(Player player) {
            PlayerState state = states.get(player.getUniqueId());
            if (state == null) return;
            if (state.killStage < 2) {
                state.killStage++;
                state.deathStage = 0;
            }
            applyEffects(player, state);
            player.sendMessage(ChatColor.GREEN + "[RandomEffects] Kill! Your effect upgraded → "
                    + ChatColor.AQUA + formatRole(state));
        }

        public void onDeath(Player player) {
            PlayerState state = states.get(player.getUniqueId());
            if (state == null) return;
            if (state.killStage > 0) {
                state.killStage--;
                state.deathStage = 0;
            } else {
                int maxDeath = (state.role == Role.REGENERATION) ? 2 : 3;
                if (state.deathStage < maxDeath) state.deathStage++;
            }
            applyEffects(player, state);
            player.sendMessage(ChatColor.RED + "[RandomEffects] You died! Your effect changed → "
                    + ChatColor.AQUA + formatRole(state));
        }

        public void reapplyEffects(Player player) {
            PlayerState state = states.get(player.getUniqueId());
            if (state != null) applyEffects(player, state);
        }

        // ── Effect application ────────────────────────────────────────────────

        private void applyEffects(Player player, PlayerState state) {
            clearEffects(player);
            if (state.deathStage > 0) applyDeath(player, state);
            else applyKill(player, state);
        }

        private void applyKill(Player player, PlayerState state) {
            switch (state.role) {
                case STRENGTH:
                    // Stage 0: Strength I | Stage 1: Strength II | Stage 2: Strength II + Resistance I
                    if (state.killStage == 0) give(player, PotionEffectType.INCREASE_DAMAGE, 0);
                    else if (state.killStage == 1) give(player, PotionEffectType.INCREASE_DAMAGE, 1);
                    else { give(player, PotionEffectType.INCREASE_DAMAGE, 1); give(player, PotionEffectType.DAMAGE_RESISTANCE, 0); }
                    break;

                case SPEED:
                    // Stage 0: Speed I | Stage 1: Speed II | Stage 2: Speed II + Haste I
                    if (state.killStage == 0) give(player, PotionEffectType.SPEED, 0);
                    else if (state.killStage == 1) give(player, PotionEffectType.SPEED, 1);
                    else { give(player, PotionEffectType.SPEED, 1); give(player, PotionEffectType.FAST_DIGGING, 0); }
                    break;

                case REGENERATION:
                    // Stage 0: Regen I | Stage 1: Regen II | Stage 2: Regen III
                    give(player, PotionEffectType.REGENERATION, state.killStage);
                    break;

                case HASTE:
                    // Stage 0: Haste I | Stage 1: Haste II | Stage 2: Haste II + Speed I
                    if (state.killStage == 0) give(player, PotionEffectType.FAST_DIGGING, 0);
                    else if (state.killStage == 1) give(player, PotionEffectType.FAST_DIGGING, 1);
                    else { give(player, PotionEffectType.FAST_DIGGING, 1); give(player, PotionEffectType.SPEED, 0); }
                    break;

                case RESISTANCE:
                    // Stage 0: Resistance I | Stage 1: Resistance II | Stage 2: Resistance II + Fire Resistance
                    if (state.killStage == 0) give(player, PotionEffectType.DAMAGE_RESISTANCE, 0);
                    else if (state.killStage == 1) give(player, PotionEffectType.DAMAGE_RESISTANCE, 1);
                    else { give(player, PotionEffectType.DAMAGE_RESISTANCE, 1); give(player, PotionEffectType.FIRE_RESISTANCE, 0); }
                    break;
            }
        }

        private void applyDeath(Player player, PlayerState state) {
            switch (state.role) {
                case STRENGTH:
                    // Stage 1: Weakness I | Stage 2: Weakness II | Stage 3: Weakness III
                    give(player, PotionEffectType.WEAKNESS, state.deathStage - 1);
                    break;

                case SPEED:
                    // Stage 1: Slowness I | Stage 2: Slowness II | Stage 3: Mining Fatigue I
                    if (state.deathStage == 1) give(player, PotionEffectType.SLOW, 0);
                    else if (state.deathStage == 2) give(player, PotionEffectType.SLOW, 1);
                    else give(player, PotionEffectType.SLOW_DIGGING, 0);
                    break;

                case REGENERATION:
                    // Stage 1: Slowness I | Stage 2: Weakness I  (max stage 2)
                    if (state.deathStage == 1) give(player, PotionEffectType.SLOW, 0);
                    else give(player, PotionEffectType.WEAKNESS, 0);
                    break;

                case HASTE:
                    // Stage 1: Mining Fatigue I | Stage 2: Mining Fatigue II | Stage 3: Mining Fatigue II + Slowness I
                    if (state.deathStage == 1) give(player, PotionEffectType.SLOW_DIGGING, 0);
                    else if (state.deathStage == 2) give(player, PotionEffectType.SLOW_DIGGING, 1);
                    else { give(player, PotionEffectType.SLOW_DIGGING, 1); give(player, PotionEffectType.SLOW, 0); }
                    break;

                case RESISTANCE:
                    // Stage 1: Weakness I | Stage 2: Slowness I | Stage 3: Mining Fatigue I
                    if (state.deathStage == 1) give(player, PotionEffectType.WEAKNESS, 0);
                    else if (state.deathStage == 2) give(player, PotionEffectType.SLOW, 0);
                    else give(player, PotionEffectType.SLOW_DIGGING, 0);
                    break;
            }
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private void give(Player p, PotionEffectType type, int amp) {
            p.addPotionEffect(new PotionEffect(type, PERMANENT, amp, true, false, true));
        }

        private void clearEffects(Player player) {
            PotionEffectType[] managed = {
                PotionEffectType.INCREASE_DAMAGE, PotionEffectType.SPEED,
                PotionEffectType.REGENERATION,    PotionEffectType.FAST_DIGGING,
                PotionEffectType.DAMAGE_RESISTANCE, PotionEffectType.FIRE_RESISTANCE,
                PotionEffectType.WEAKNESS,        PotionEffectType.SLOW,
                PotionEffectType.SLOW_DIGGING
            };
            for (PotionEffectType t : managed) player.removePotionEffect(t);
        }

        public String formatRole(PlayerState state) {
            if (state.deathStage > 0) return formatDeath(state);
            return formatKill(state);
        }

        private String formatKill(PlayerState state) {
            switch (state.role) {
                case STRENGTH:
                    if (state.killStage == 0) return "Strength I";
                    if (state.killStage == 1) return "Strength II";
                    return "Strength II + Resistance I";
                case SPEED:
                    if (state.killStage == 0) return "Speed I";
                    if (state.killStage == 1) return "Speed II";
                    return "Speed II + Haste I";
                case REGENERATION:
                    if (state.killStage == 0) return "Regeneration I";
                    if (state.killStage == 1) return "Regeneration II";
                    return "Regeneration III";
                case HASTE:
                    if (state.killStage == 0) return "Haste I";
                    if (state.killStage == 1) return "Haste II";
                    return "Haste II + Speed I";
                case RESISTANCE:
                    if (state.killStage == 0) return "Resistance I";
                    if (state.killStage == 1) return "Resistance II";
                    return "Resistance II + Fire Resistance";
                default: return "Unknown";
            }
        }

        private String formatDeath(PlayerState state) {
            switch (state.role) {
                case STRENGTH:   return "Weakness " + roman(state.deathStage);
                case SPEED:
                    if (state.deathStage <= 2) return "Slowness " + roman(state.deathStage);
                    return "Mining Fatigue I";
                case REGENERATION:
                    return state.deathStage == 1 ? "Slowness I" : "Weakness I";
                case HASTE:
                    if (state.deathStage == 1) return "Mining Fatigue I";
                    if (state.deathStage == 2) return "Mining Fatigue II";
                    return "Mining Fatigue II + Slowness I";
                case RESISTANCE:
                    if (state.deathStage == 1) return "Weakness I";
                    if (state.deathStage == 2) return "Slowness I";
                    return "Mining Fatigue I";
                default: return "Unknown";
            }
        }

        private String roman(int n) {
            switch (n) { case 1: return "I"; case 2: return "II"; case 3: return "III"; default: return String.valueOf(n); }
        }
    }

    // =========================================================================
    //  EVENT LISTENER
    // =========================================================================
    public static class Events implements Listener {

        private final RandomEffectsPlugin plugin;
        private final EffectManager em;

        public Events(RandomEffectsPlugin plugin, EffectManager em) {
            this.plugin = plugin;
            this.em = em;
        }

        @EventHandler
        public void onJoin(PlayerJoinEvent e) {
            Player player = e.getPlayer();
            if (!em.hasRole(player)) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(ChatColor.GOLD + "[RandomEffects] " + ChatColor.GRAY
                                + "You don't have a role yet. Ask an operator to run "
                                + ChatColor.YELLOW + "/rollrole " + player.getName());
                    }
                }, 20L);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onDeath(EntityDeathEvent e) {
            Entity killed = e.getEntity();
            if (!(killed instanceof Player)) return;

            Player victim = (Player) killed;
            Player killer = victim.getKiller();
            if (killer == null || killer.equals(victim)) return;

            em.onDeath(victim);
            em.onKill(killer);
        }

        @EventHandler
        public void onRespawn(PlayerRespawnEvent e) {
            Player player = e.getPlayer();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) em.reapplyEffects(player);
            }, 5L);
        }
    }

    // =========================================================================
    //  COMMAND HANDLER
    // =========================================================================
    public static class CommandHandler implements CommandExecutor {

        private final RandomEffectsPlugin plugin;
        private final EffectManager em;

        public CommandHandler(RandomEffectsPlugin plugin, EffectManager em) {
            this.plugin = plugin;
            this.em = em;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            switch (command.getName().toLowerCase()) {

                // ── /rollroles ────────────────────────────────────────────────
                case "rollroles": {
                    if (!sender.hasPermission("randomeffects.rollroles")) {
                        sender.sendMessage(ChatColor.RED + "No permission."); return true;
                    }
                    if (em.isGlobalRollDone()) {
                        sender.sendMessage(ChatColor.RED + "[RandomEffects] Roles already rolled! Use /rollrole <player> for individuals.");
                        return true;
                    }
                    int count = 0;
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (!em.hasRole(p)) { em.assignRandomRole(p); count++; }
                    }
                    em.setGlobalRollDone(true);
                    Bukkit.broadcastMessage(ChatColor.GOLD + "[RandomEffects] " + ChatColor.YELLOW
                            + "Roles assigned to " + count + " player(s)! The games begin!");
                    return true;
                }

                // ── /rollrole <player> ────────────────────────────────────────
                case "rollrole": {
                    if (!sender.hasPermission("randomeffects.rollrole")) {
                        sender.sendMessage(ChatColor.RED + "No permission."); return true;
                    }
                    if (args.length < 1) { sender.sendMessage(ChatColor.RED + "Usage: /rollrole <player>"); return true; }
                    Player target = Bukkit.getPlayerExact(args[0]);
                    if (target == null) { sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]); return true; }
                    if (em.hasRole(target)) {
                        sender.sendMessage(ChatColor.YELLOW + "[RandomEffects] " + target.getName() + " already has a role! Use /rerollrole to reset.");
                        return true;
                    }
                    em.assignRandomRole(target);
                    sender.sendMessage(ChatColor.GREEN + "[RandomEffects] Role assigned to " + target.getName() + ".");
                    return true;
                }

                // ── /rerollrole <player> ──────────────────────────────────────
                case "rerollrole": {
                    if (!sender.hasPermission("randomeffects.rerollrole")) {
                        sender.sendMessage(ChatColor.RED + "No permission."); return true;
                    }
                    if (args.length < 1) { sender.sendMessage(ChatColor.RED + "Usage: /rerollrole <player>"); return true; }
                    Player target = Bukkit.getPlayerExact(args[0]);
                    if (target == null) { sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]); return true; }
                    em.removePlayer(target.getUniqueId());
                    em.assignRandomRole(target);
                    sender.sendMessage(ChatColor.GREEN + "[RandomEffects] " + target.getName() + "'s role has been re-rolled!");
                    target.sendMessage(ChatColor.GOLD + "[RandomEffects] " + ChatColor.YELLOW + "An operator re-rolled your role!");
                    return true;
                }

                // ── /checkrole [player] ───────────────────────────────────────
                case "checkrole": {
                    if (args.length == 0) {
                        // Check self
                        if (!(sender instanceof Player)) {
                            sender.sendMessage(ChatColor.RED + "Console must specify a player: /checkrole <player>"); return true;
                        }
                        Player self = (Player) sender;
                        if (!em.hasRole(self)) {
                            sender.sendMessage(ChatColor.YELLOW + "[RandomEffects] You don't have a role yet."); return true;
                        }
                        EffectManager.PlayerState s = em.getState(self.getUniqueId());
                        sender.sendMessage(buildRoleMessage("Your", s));
                    } else {
                        // Check another player — op only
                        if (!sender.hasPermission("randomeffects.checkrole.others")) {
                            sender.sendMessage(ChatColor.RED + "No permission to check other players."); return true;
                        }
                        Player target = Bukkit.getPlayerExact(args[0]);
                        if (target == null) { sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]); return true; }
                        if (!em.hasRole(target)) {
                            sender.sendMessage(ChatColor.YELLOW + "[RandomEffects] " + target.getName() + " doesn't have a role yet."); return true;
                        }
                        EffectManager.PlayerState s = em.getState(target.getUniqueId());
                        sender.sendMessage(buildRoleMessage(target.getName() + "'s", s));
                    }
                    return true;
                }

                default: return false;
            }
        }

        private String buildRoleMessage(String who, EffectManager.PlayerState s) {
            return ChatColor.GOLD + "[RandomEffects] " + ChatColor.AQUA + who + " role: "
                    + ChatColor.WHITE + em.formatRole(s)
                    + ChatColor.GRAY + "  |  Base: " + ChatColor.YELLOW + s.role.name()
                    + ChatColor.GRAY + "  |  Kill stage: " + ChatColor.GREEN + s.killStage + "/2"
                    + ChatColor.GRAY + "  |  Death stage: " + ChatColor.RED + s.deathStage
                    + "/" + (s.role == EffectManager.Role.REGENERATION ? "2" : "3");
        }
    }
}
