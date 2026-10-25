package com.wabihari.aichatbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AIChatbotPlugin extends JavaPlugin {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();
    private HttpClient httpClient;
    private AIMapGenerator aiMapGenerator;
    private AIMusicGenerator aiMusicGenerator;

    @Override
    public void onEnable() {
    saveDefaultConfig();
    rebuildHttpClient();

    aiMapGenerator = new AIMapGenerator(this);
    aiMusicGenerator = new AIMusicGenerator(this);

    getLogger().info("AIChatbotPlugin enabled!");
}
    private void rebuildHttpClient() {
        int timeout = Math.max(5, getConfig().getInt("ai.request-timeout-seconds", 30));
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeout)).build();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("aimusic")) {
    if (!sender.hasPermission("aichatbot.music")) {
        sender.sendMessage("§cYou don't have permission to generate music!");
        return true;
    }

    if (!(sender instanceof Player player)) {
        sender.sendMessage("This command can only be used in-game.");
        return true;
    }

    if (!getConfig().getBoolean("aimusic.enabled", true)) {
        player.sendMessage("§cMusic generation is disabled.");
        return true;
    }

    if (args.length == 0) {
        player.sendMessage("§eUsage: /aimusic <description>");
        return true;
    }

    int cooldown = Math.max(
            0,
            getConfig().getInt("aimusic.cooldown-seconds", 60)
    );

    long now = System.currentTimeMillis();
    long previous = lastUse.getOrDefault(player.getUniqueId(), 0L);
    long remaining = cooldown - ((now - previous) / 1000L);

    if (remaining > 0) {
        player.sendMessage("§cPlease wait " + remaining
                + " seconds before generating music again.");
        return true;
    }

    lastUse.put(player.getUniqueId(), now);

    String prompt = String.join(" ", args).trim();

    player.sendMessage("§aGenerating your music! Please wait...");

    aiMusicGenerator.generate(player, prompt);
    return true;
}
        if (command.getName().equalsIgnoreCase("aimap")) {
    if (!sender.hasPermission("aichatbot.map")) {
        sender.sendMessage("§cYou don't have permissions!");
        return true;
    }

    if (!(sender instanceof Player player)) {
        sender.sendMessage("This command is only aviable on the Server.");
        return true;
    }

    if (args.length == 0) {
        player.sendMessage("§eUse: /aimap <Discription>");
        return true;
    }

    if (!getConfig().getBoolean("aimap.enabled", true)) {
        player.sendMessage("§cThis feature is deactivated.");
        return true;
    }

    String prompt = String.join(" ", args);

    player.sendMessage("§aYour image is being generated! Please wait...");
    aiMapGenerator.generate(player, prompt);

    return true;
}
        if (command.getName().equalsIgnoreCase("ai")) {
            if (!sender.hasPermission("aichatbot.use")) {
                sendConfigured(sender, "messages.no-permission", "&cYou don't have permissions to do this.");
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("aichatbot.admin")) {
                reloadConfig();
                rebuildHttpClient();
                sendConfigured(sender, "messages.reloaded", "&aReloading the files...");
                return true;
            }
            if (args.length == 0) {
                sendConfigured(sender, "messages.usage", "&eUse: /ai <chat>");
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command is only aviable on the Server.");
                return true;
            }
            int cooldown = Math.max(0, getConfig().getInt("ai.cooldown-seconds", 3));
            long now = System.currentTimeMillis();
            long previous = lastUse.getOrDefault(player.getUniqueId(), 0L);
            long remaining = cooldown - ((now - previous) / 1000L);
            if (remaining > 0) {
                sendConfigured(player, "messages.cooldown", "&cPlease wait {seconds} seconds.", "{seconds}", Long.toString(remaining));
                return true;
            }
            lastUse.put(player.getUniqueId(), now);
            String prompt = String.join(" ", args).trim();
            sendConfigured(player, "messages.thinking", "&7AI is thinking...");
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> askOllama(player.getUniqueId(), player.getName(), prompt));
            return true;
        }
        return false;
    }

    private void askOllama(UUID playerId, String playerName, String prompt) {
        try {
            FileConfiguration config = getConfig();
            String baseUrl = config.getString("ai.base-url", "https://api.groq.com/openai/v1").trim().replaceAll("/+$", "");
            String model = config.getString("ai.model", "llama-3.3-70b-versatile");
            String systemPrompt = config.getString("ai.system-prompt", "You are a helpful assistant.");
            int maxTokens = Math.max(1, config.getInt("ai.max-tokens", 300));

            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("stream", false);
            body.addProperty("max_completion_tokens", maxTokens);
JsonArray messages = new JsonArray();
            JsonObject system = new JsonObject(); system.addProperty("role", "system"); system.addProperty("content", systemPrompt); messages.add(system);
            JsonObject user = new JsonObject(); user.addProperty("role", "user"); user.addProperty("content", "Minecraft-Spieler " + playerName + " fragt: " + prompt); messages.add(user);
            body.add("messages", messages); 

            int timeout = Math.max(5, config.getInt("ai.request-timeout-seconds", 30));
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
            String apiKey = config.getString("ai.api-key", "").trim();
            if (!apiKey.isEmpty()) requestBuilder.header("Authorization", "Bearer " + apiKey);

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                getLogger().warning("Ollama endpoint returned HTTP " + response.statusCode());
                sendError(playerId);
                return;
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String answer = json
            .getAsJsonArray("choices")
            .get(0).getAsJsonObject()
            .getAsJsonObject("message")
            .get("content").getAsString().trim();
            if (answer.isBlank()) answer = "Ich habe gerade keine Antwort.";
            String finalAnswer = answer;
            Bukkit.getScheduler().runTask(this, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    String prefix = getConfig().getString("messages.prefix", "&8[&bAI&8] &r");
                    player.sendMessage(LEGACY.deserialize(prefix + "&f" + finalAnswer));
                }
            });
        } catch (Exception exception) {
            getLogger().warning("AI request failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            sendError(playerId);
        }
    }

    private void sendError(UUID playerId) {
        Bukkit.getScheduler().runTask(this, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) sendConfigured(player, "messages.error", "&cThe AI is currently unavailable. Please try again later.");

        });
    }

    private void sendConfigured(CommandSender sender, String path, String fallback) {
        sender.sendMessage(LEGACY.deserialize(getConfig().getString(path, fallback)));
    }

    private void sendConfigured(CommandSender sender, String path, String fallback, String placeholder, String value) {
        sender.sendMessage(LEGACY.deserialize(getConfig().getString(path, fallback).replace(placeholder, value)));
    }
}
