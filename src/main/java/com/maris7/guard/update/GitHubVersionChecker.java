package com.maris7.guard.update;

import com.maris7.guard.MarisGuard;
import com.maris7.guard.antiesp.util.ColorUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHubVersionChecker {
    private static final URI LATEST_RELEASE_URI = URI.create("https://api.github.com/repos/cocokea/MarisGuard/releases/latest");
    private static final Pattern TAG_NAME_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");

    private final MarisGuard plugin;
    private final HttpClient httpClient;

    public GitHubVersionChecker(MarisGuard plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void checkAsync() {
        CompletableFuture.runAsync(this::checkNow);
    }

    private void checkNow() {
        try {
            HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE_URI)
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "MarisGuard/" + plugin.getDescription().getVersion())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                warn();
                return;
            }

            String latestVersion = extractTagName(response.body());
            if (latestVersion == null || latestVersion.isBlank()) {
                warn();
                return;
            }

            String currentVersion = normalizeVersion(plugin.getDescription().getVersion());
            latestVersion = normalizeVersion(latestVersion);

            if (compareVersions(currentVersion, latestVersion) >= 0) {
                plugin.getLogger().info(ColorUtil.colorize("&fYou are currently using the latest version (&a" + currentVersion + "&f)"));
                return;
            }

            plugin.getLogger().info(ColorUtil.colorize("&fThe new version has been released (&a" + latestVersion + "&f)"));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            warn();
        } catch (IOException | RuntimeException exception) {
            warn();
        }
    }

    private static String extractTagName(String body) {
        Matcher matcher = TAG_NAME_PATTERN.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String normalizeVersion(String version) {
        String normalized = version == null ? "" : version.trim();
        while (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static int compareVersions(String left, String right) {
        String[] leftParts = left.split("[^0-9A-Za-z]+");
        String[] rightParts = right.split("[^0-9A-Za-z]+");
        int length = Math.max(leftParts.length, rightParts.length);

        for (int index = 0; index < length; index++) {
            String leftPart = index < leftParts.length ? leftParts[index] : "0";
            String rightPart = index < rightParts.length ? rightParts[index] : "0";
            int comparison = comparePart(leftPart, rightPart);
            if (comparison != 0) {
                return comparison;
            }
        }

        return 0;
    }

    private static int comparePart(String left, String right) {
        boolean leftNumeric = left.chars().allMatch(Character::isDigit);
        boolean rightNumeric = right.chars().allMatch(Character::isDigit);

        if (leftNumeric && rightNumeric) {
            return Integer.compare(parseInt(left), parseInt(right));
        }

        if (leftNumeric) {
            return 1;
        }
        if (rightNumeric) {
            return -1;
        }

        return left.compareToIgnoreCase(right);
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void warn() {
        plugin.getLogger().warning("Unable to check for the new version.");
    }
}
