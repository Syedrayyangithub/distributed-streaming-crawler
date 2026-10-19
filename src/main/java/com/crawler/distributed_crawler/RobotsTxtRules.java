package com.crawler.distributed_crawler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RobotsTxtRules {

    private final List<String> disallows;

    RobotsTxtRules(List<String> disallows) {
        this.disallows = List.copyOf(disallows);
    }

    public static RobotsTxtRules allowAll() {
        return new RobotsTxtRules(List.of());
    }

    public static RobotsTxtRules parse(String robotsTxt, String botName) {
        if (robotsTxt == null || robotsTxt.isBlank()) {
            return allowAll();
        }

        String bot = botName.toLowerCase(Locale.ROOT);
        List<String> matchedDisallows = new ArrayList<>();
        List<String> currentAgents = new ArrayList<>();
        List<String> currentDisallows = new ArrayList<>();

        for (String rawLine : robotsTxt.split("\\R")) {
            String line = rawLine.replaceFirst("#.*", "").trim();
            if (line.isEmpty()) {
                applyGroup(bot, currentAgents, currentDisallows, matchedDisallows);
                currentAgents = new ArrayList<>();
                currentDisallows = new ArrayList<>();
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String field = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (field.equals("user-agent")) {
                if (!currentDisallows.isEmpty()) {
                    applyGroup(bot, currentAgents, currentDisallows, matchedDisallows);
                    currentAgents = new ArrayList<>();
                    currentDisallows = new ArrayList<>();
                }
                currentAgents.add(value.toLowerCase(Locale.ROOT));
            } else if (field.equals("disallow")) {
                currentDisallows.add(value);
            }
        }
        applyGroup(bot, currentAgents, currentDisallows, matchedDisallows);
        return new RobotsTxtRules(matchedDisallows);
    }

    public boolean allows(String url) {
        String path = pathOf(url);
        for (String disallow : disallows) {
            if (!disallow.isEmpty() && path.startsWith(disallow)) {
                return false;
            }
        }
        return true;
    }

    private static void applyGroup(String bot,
                                   List<String> agents,
                                   List<String> groupDisallows,
                                   List<String> matchedDisallows) {
        boolean applies = agents.stream().anyMatch(agent ->
                agent.equals("*") || bot.startsWith(agent) || agent.startsWith(bot));
        if (applies) {
            matchedDisallows.addAll(groupDisallows);
        }
    }

    private static String pathOf(String url) {
        try {
            String path = URI.create(url).getPath();
            return path == null || path.isBlank() ? "/" : path;
        } catch (Exception e) {
            return "/";
        }
    }
}
