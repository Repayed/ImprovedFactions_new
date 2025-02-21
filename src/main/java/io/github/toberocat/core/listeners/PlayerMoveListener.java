package io.github.toberocat.core.listeners;

import io.github.toberocat.MainIF;
import io.github.toberocat.core.commands.factions.unclaim.UnclaimOneSubCommand;
import io.github.toberocat.core.factions.Faction;
import io.github.toberocat.core.factions.FactionUtility;
import io.github.toberocat.core.utility.async.AsyncTask;
import io.github.toberocat.core.utility.claim.ClaimManager;
import io.github.toberocat.core.utility.history.History;
import io.github.toberocat.core.utility.history.territory.Territory;
import io.github.toberocat.core.utility.language.Language;
import io.github.toberocat.core.utility.language.Parseable;
import io.github.toberocat.core.utility.settings.PlayerSettings;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public class PlayerMoveListener implements Listener {

    public static Map<UUID, ClaimAutoType> AUTO_CLAIM_OPERATIONS = new HashMap<>();
    public static Map<UUID, HashMap<String, Consumer<Player>>> MOVE_OPERATIONS = new HashMap<>();

    @EventHandler
    public void OnMove(PlayerMoveEvent event) {
        Chunk from = event.getFrom().getChunk();
        Chunk to = Objects.requireNonNull(event.getTo()).getChunk();
        Player player = event.getPlayer();

        if (from != to) {
            String fromRegistry = MainIF.getIF().getClaimManager().getFactionRegistry(from);
            String toRegistry = MainIF.getIF().getClaimManager().getFactionRegistry(to);

            if (AUTO_CLAIM_OPERATIONS.containsKey(player.getUniqueId())) {
                ClaimAutoType operation = AUTO_CLAIM_OPERATIONS.get(player.getUniqueId());
                claimOp(operation, player);
            }

            if (MOVE_OPERATIONS.containsKey(player.getUniqueId())) {
                HashMap<String, Consumer<Player>> use = MOVE_OPERATIONS.get(player.getUniqueId());
                for (Consumer<Player> run : use.values()) {
                    run.accept(player);
                }
            }

            if (toRegistry == null && fromRegistry == null) return;

            if (!FactionUtility.doesFactionExist(toRegistry)) {
                MainIF.getIF().getClaimManager().removeProtection(to);
            }

            if (fromRegistry == null) {
                display(player, "wildness", toRegistry, from, to);
                return;
            } else if (toRegistry == null) {
                display(player, fromRegistry, "wildness", from, to);
                return;
            }

            if (!fromRegistry.equals(toRegistry)) {
                display(player, fromRegistry, toRegistry, from, to);
            }
        }
    }

    private void claimOp(ClaimAutoType auto, Player player) {
        Faction faction = FactionUtility.getPlayerFaction(player);

        if (faction == null) return;

        if (auto == ClaimAutoType.UNCLAIM) {
            UnclaimOneSubCommand.unclaim(player);
        }
    }

    private void display(Player player, String fromRegistry, String toRegistry, Chunk from, Chunk to) {
        History.logTerritorySwitch(player, new Territory(fromRegistry, from),
                new Territory(toRegistry, to));

        PlayerSettings settings = PlayerSettings.getSettings(player.getUniqueId());
        if (!(Boolean) settings.getSetting("displayTitle").getSelected()) return;

        PlayerSettings.TitlePosition position = PlayerSettings.TitlePosition.values()[(int) settings.getSetting("titlePosition").getSelected()];

        String registry = MainIF.getIF().getClaimManager().getFactionRegistry(to);

        sendTitle(position, player, registry);
    }

    private void sendTitle(PlayerSettings.TitlePosition pos, Player player, String registry) {
        Faction faction = FactionUtility.getFactionByRegistry(registry);
        Faction playerFaction = FactionUtility.getPlayerFaction(player);

        AsyncTask.run(() -> {
            String text = null;

            if (registry == null) {
                text = Language.getMessage("territory.wilderness", player);
            } else if (faction != null) {
                text = faction.getDisplayName();
            } else if (registry.equals(ClaimManager.SAFEZONE_REGISTRY)) {
                text = Language.getMessage("territory.safezone", player);
            } else if (registry.equals(ClaimManager.WARZONE_REGISTRY)) {
                text = Language.getMessage("territory.warzone", player);
            } else if (registry.equals(ClaimManager.UNCLAIMABLE_REGISTRY)) {
                return;
            }

            String relation = "&e";
            if (playerFaction != null && faction != null) {
                if (playerFaction.getRegistryName().equals(registry)) {
                    relation = "&a";
                } else if (playerFaction.getRelationManager().getAllies().contains(faction.getRegistryName())) {
                    relation = "&b";
                } else if (playerFaction.getRelationManager().getEnemies().contains(faction.getRegistryName())) {
                    relation = "&c";
                }
            }

            if (text == null) return;

            Parseable[] parses = new Parseable[]{new Parseable("{territory}", text),
                    new Parseable("{relation}", relation)};

            switch (pos) {
                case CHAT -> Language.sendMessage("territory.entered.chat", player, parses);
                case ACTIONBAR -> player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(Language.getMessage("territory.entered.actionbar",
                                player, parses)));
                case TITLE -> player.sendTitle(Language.getMessage("territory.entered.title",
                        player, parses), "", 5, 20, 5);
                case SUBTITLE -> player.sendTitle(" ", Language.getMessage("territory.entered.subtitle",
                        player, parses), 5, 20, 5);
            }
        });
    }

    public enum ClaimAutoType {CLAIM, UNCLAIM}
}
