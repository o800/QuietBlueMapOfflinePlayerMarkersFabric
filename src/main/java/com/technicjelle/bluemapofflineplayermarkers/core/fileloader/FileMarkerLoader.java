package com.technicjelle.bluemapofflineplayermarkers.core.fileloader;

import com.technicjelle.bluemapofflineplayermarkers.core.Player;
import com.technicjelle.bluemapofflineplayermarkers.core.Singletons;
import com.technicjelle.bluemapofflineplayermarkers.impl.fabric.BluemapOfflinePlayerMarkers;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluenbt.BlueNBT;
import de.bluecolored.bluenbt.NBTReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class FileMarkerLoader {
    private static final BlueNBT nbt = new BlueNBT();

    public static int files_loaded = 0;
    public static int markers_added = 0;
    public static int expired_players = 0;

    public static void loadOfflineMarkers() {
        Path playerDataFolder = Singletons.getServer().getPlayerDataFolder();

        //Return if playerdata is missing for some reason.
        if (!Files.exists(playerDataFolder) || !Files.isDirectory(playerDataFolder)) {
            BluemapOfflinePlayerMarkers.LOGGER.error("Playerdata folder not found, skipping loading of offline markers from storage");
            return;
        }

        BlueMapAPI api;
        if (Singletons.isBlueMapAPIPresent()) {
            if (BlueMapAPI.getInstance().isPresent())
                api = BlueMapAPI.getInstance().get();
            else {
                BluemapOfflinePlayerMarkers.LOGGER.warn("BlueMapAPI not available, skipping loading of offline markers from storage");
                return;
            }
        } else {
            BluemapOfflinePlayerMarkers.LOGGER.info("BlueMapAPI not available, probably due to running in a test environment");
            api = null;
        }

        try (Stream<Path> playerDataFiles = Files.list(playerDataFolder)) {
            playerDataFiles.filter(p -> p.toString().endsWith(".dat")).forEach(p -> loadOfflineMarker(p, api));
        } catch (IOException e) {
            BluemapOfflinePlayerMarkers.LOGGER.error("Failed to stream playerdata", e);
        }
        BluemapOfflinePlayerMarkers.LOGGER.info("{} playerdata files loaded, {} markers added, and {} expired player markers not added", files_loaded, markers_added, expired_players);
    }

    private static void loadOfflineMarker(Path playerDataFile, BlueMapAPI api) {
        final String fileName = playerDataFile.getFileName().toString();
        files_loaded++;

        final String uuidString = fileName.replace(".dat", "");
        final UUID playerUUID;
        try {
            playerUUID = UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            BluemapOfflinePlayerMarkers.LOGGER.warn("Invalid playerdata filename: {}, skipping", fileName);
            return;
        }

        if (playerDataFile.toFile().length() == 0) {
            BluemapOfflinePlayerMarkers.LOGGER.warn("Playerdata file {} is empty, skipping", fileName);
            return;
        }

        if (Singletons.getServer().isPlayerOnline(playerUUID)) return; // don't add markers for online players

        if (Singletons.getConfig().checkPlayerLastPlayed(playerUUID)) {
            expired_players++;
            return;
        }

        try (GZIPInputStream in = new GZIPInputStream(Files.newInputStream(playerDataFile))) {
            NBTReader reader = new NBTReader(in);
            PlayerNBTData playerNBTData = nbt.read(reader, PlayerNBTData.class);

            Player player = new Player(playerUUID, playerNBTData);
            Singletons.getMarkerHandler().add(player, api);
        } catch (IOException e) {
            BluemapOfflinePlayerMarkers.LOGGER.error("Failed to read playerdata file {}", fileName, e);
        }
    }
}
