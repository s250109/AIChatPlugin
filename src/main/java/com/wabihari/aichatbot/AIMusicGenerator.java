package com.wabihari.aichatbot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

public final class AIMusicGenerator {

    private final JavaPlugin plugin;
    private final HttpClient httpClient;

    public AIMusicGenerator(JavaPlugin plugin) {
        this.plugin = plugin;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public void generate(Player player, String prompt) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String accountId = plugin.getConfig()
                        .getString("aimusic.cloudflare.account-id", "")
                        .trim();

                String apiToken = plugin.getConfig()
                        .getString("aimusic.cloudflare.api-token", "")
                        .trim();

                String model = plugin.getConfig()
                        .getString("aimusic.cloudflare.model", "elevenlabs/music-v2")
                        .trim();

                if (accountId.isBlank() || apiToken.isBlank()
                        || accountId.equals("YOUR_ACCOUNT_ID")
                        || apiToken.equals("YOUR_CLOUDFLARE_TOKEN")) {
                    sendMessage(playerId,
                            "§cMusic generation isn't configured yet.");
                    return;
                }

                int timeout = Math.max(10, plugin.getConfig()
                        .getInt("aimusic.request-timeout-seconds", 120));

                JsonObject input = new JsonObject();
                input.addProperty("prompt", prompt);
                input.addProperty("music_length_ms", 30000);
                input.addProperty("force_instrumental", false);
                input.addProperty("output_format", "mp3_48000_192");

                JsonObject body = new JsonObject();
                body.add("input", input);
                body.addProperty("model", model);

                String url = "https://api.cloudflare.com/client/v4/accounts/"
                        + accountId + "/ai/run/" + model;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(timeout))
                        .header("Authorization", "Bearer " + apiToken)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();

                HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

                if (response.statusCode() < 200
                        || response.statusCode() >= 300) {
                    plugin.getLogger().warning(
                            "Music request failed with HTTP "
                                    + response.statusCode()
                    );

                    sendMessage(playerId,
                            "§cMusic generation failed. Check the server console.");
                    return;
                }

                JsonObject json = JsonParser.parseString(response.body())
                        .getAsJsonObject();

                JsonObject result = json.getAsJsonObject("result");

                if (result == null || !result.has("audio")) {
                    plugin.getLogger().warning(
                            "Music response did not contain audio."
                    );
                    sendMessage(playerId,
                            "§cThe AI didn't return an audio file.");
                    return;
                }

                String audioValue = result.get("audio").getAsString();

                byte[] audioBytes;

                if (audioValue.startsWith("data:")) {
                    int comma = audioValue.indexOf(',');
                    if (comma < 0) {
                        throw new IOException("Invalid audio data URI.");
                    }
                    audioBytes = Base64.getDecoder().decode(
                            audioValue.substring(comma + 1)
                    );
                } else {
                    audioBytes = Base64.getDecoder().decode(audioValue);
                }

                Path musicDirectory = plugin.getDataFolder()
                        .toPath()
                        .resolve("music");

                Files.createDirectories(musicDirectory);

                String safeName = playerName.replaceAll(
                        "[^a-zA-Z0-9_-]", "_"
                );

                String fileName = safeName + "_"
                        + System.currentTimeMillis() + ".mp3";

                Path outputFile = musicDirectory.resolve(fileName);

                Files.write(outputFile, audioBytes);

                plugin.getLogger().info(
                        "Generated music saved to " + outputFile
                );

                sendMessage(playerId,
                        "§aYour music has been generated! §7Saved as §f"
                                + fileName);

            } catch (Exception exception) {
                plugin.getLogger().warning(
                        "Music generation failed: "
                                + exception.getClass().getSimpleName()
                                + ": " + exception.getMessage()
                );

                sendMessage(playerId,
                        "§cMusic generation failed. Check the server console.");
            }
        });
    }

    private void sendMessage(UUID playerId, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);

            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        });
    }
}
