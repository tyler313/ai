package com.aicompanion.ai;

import com.aicompanion.AIMod;
import com.aicompanion.player.AIPlayer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Long-term memory for an AI: home base, waypoints, known resources,
 * explored areas, and player preferences. Persisted to disk as JSON.
 */
public class Memory {
    private final AIPlayer ai;
    private final Path file;

    public BlockPos home = null;
    public BlockPos lastKnownPlayerPos = null;
    public BlockPos bedPos = null;
    public BlockPos chestPos = null;
    public BlockPos craftingTablePos = null;
    public BlockPos furnacePos = null;

    /** Known resource locations (block name -> positions), capped size. */
    public final Map<String, List<BlockPos>> knownResources = new LinkedHashMap<>();
    /** Areas already mined out / explored (to avoid re-searching). */
    public final Set<String> exploredChunks = new HashSet<>();
    /** Waypoints by name. */
    public final Map<String, BlockPos> waypoints = new HashMap<>();
    /** Death location. */
    public BlockPos deathPos = null;

    public int kills = 0;
    public int blocksMined = 0;
    public int blocksPlaced = 0;
    public int itemsCollected = 0;
    public int deaths = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public Memory(AIPlayer ai) {
        this.ai = ai;
        this.file = FabricLoader.getInstance().getConfigDir()
                .resolve("aicompanion").resolve(ai.name().toLowerCase() + "_memory.json");
        load();
    }

    public void rememberResource(String blockName, BlockPos pos) {
        List<BlockPos> list = knownResources.computeIfAbsent(blockName, k -> new ArrayList<>());
        if (!list.contains(pos)) {
            list.add(pos.toImmutable());
            if (list.size() > 32) list.remove(0);
        }
    }

    public List<BlockPos> getKnown(String blockName) {
        return knownResources.getOrDefault(blockName, List.of());
    }

    public void setHome(BlockPos pos) {
        this.home = pos != null ? pos.toImmutable() : null;
        save();
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            JsonObject o = new JsonObject();
            o.addProperty("home", posToString(home));
            o.addProperty("bed", posToString(bedPos));
            o.addProperty("chest", posToString(chestPos));
            o.addProperty("craftingTable", posToString(craftingTablePos));
            o.addProperty("furnace", posToString(furnacePos));
            o.addProperty("death", posToString(deathPos));
            o.addProperty("kills", kills);
            o.addProperty("blocksMined", blocksMined);
            o.addProperty("blocksPlaced", blocksPlaced);
            o.addProperty("itemsCollected", itemsCollected);
            o.addProperty("deaths", deaths);
            JsonObject wp = new JsonObject();
            for (Map.Entry<String, BlockPos> e : waypoints.entrySet()) {
                wp.addProperty(e.getKey(), posToString(e.getValue()));
            }
            o.add("waypoints", wp);
            Files.writeString(file, GSON.toJson(o));
        } catch (Exception e) {
            AIMod.LOGGER.warn("[AI Companion] Failed to save memory", e);
        }
    }

    private void load() {
        try {
            if (!Files.exists(file)) return;
            JsonObject o = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            home = parsePos(o, "home");
            bedPos = parsePos(o, "bed");
            chestPos = parsePos(o, "chest");
            craftingTablePos = parsePos(o, "craftingTable");
            furnacePos = parsePos(o, "furnace");
            deathPos = parsePos(o, "death");
            if (o.has("kills")) kills = o.get("kills").getAsInt();
            if (o.has("blocksMined")) blocksMined = o.get("blocksMined").getAsInt();
            if (o.has("blocksPlaced")) blocksPlaced = o.get("blocksPlaced").getAsInt();
            if (o.has("itemsCollected")) itemsCollected = o.get("itemsCollected").getAsInt();
            if (o.has("deaths")) deaths = o.get("deaths").getAsInt();
            if (o.has("waypoints")) {
                JsonObject wp = o.getAsJsonObject("waypoints");
                for (String k : wp.keySet()) {
                    BlockPos p = parsePosString(wp.get(k).getAsString());
                    if (p != null) waypoints.put(k, p);
                }
            }
        } catch (Exception e) {
            AIMod.LOGGER.warn("[AI Companion] Failed to load memory", e);
        }
    }

    private static String posToString(BlockPos p) {
        return p == null ? "" : p.getX() + "," + p.getY() + "," + p.getZ();
    }

    private static BlockPos parsePos(JsonObject o, String key) {
        if (!o.has(key)) return null;
        return parsePosString(o.get(key).getAsString());
    }

    private static BlockPos parsePosString(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            String[] parts = s.split(",");
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (Exception e) {
            return null;
        }
    }
}
