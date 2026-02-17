package org.example.service.player.search;

import org.example.model.PlayerMatch;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractPlayerSearch implements PlayerSearch {

    private static final Pattern ID_PATTERN = Pattern.compile("id=([a-f0-9]+)");

    // Абстрактные методы для специфичной логики
    protected abstract String getBaseUrl();
    protected abstract String getNameQueryParameter();
    protected abstract Elements getRows(Document doc);
    protected abstract String getName(Element row);
    protected abstract String getCity(Element row);
    protected abstract String getRatingText(Element row);
    protected abstract Element getLinkElement(Element row);

    @Override
    public List<PlayerMatch> searchByName(String rawName) throws Exception {
        if (rawName == null || rawName.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String cleanName = rawName.trim().replace("⁠", " ").replaceAll("\\s+", " ");
        String encodedName = URLEncoder.encode(cleanName, StandardCharsets.UTF_8);
        String url = getBaseUrl() + encodedName;

        Connection connection = Jsoup.connect(url).timeout(10000);
        Document doc = executeWithRetry(connection, 3);

        Elements rows = getRows(doc);
        List<PlayerMatch> matches = new ArrayList<>();

        for (Element row : rows) {
            try {
                String name = getName(row).trim();
                String city = getCity(row).trim();
                Integer rating = parseRating(getRatingText(row));
                String playerId = extractPlayerId(row);

                if (playerId != null && !playerId.isEmpty()) {
                    matches.add(new PlayerMatch(name, city, rating, playerId));
                }
            } catch (Exception e) {
                System.err.println("Ошибка при парсинге строки: " + e.getMessage());
            }
        }

        return matches;
    }

    private Document executeWithRetry(Connection connection, int maxRetries) throws Exception {
        Exception lastException = null;
        for (int i = 0; i < maxRetries; i++) {
            try {
                return connection.get();
            } catch (Exception e) {
                lastException = e;
                Thread.sleep(1000L * (i + 1));
            }
        }
        throw lastException;
    }

    private Integer parseRating(String ratingStr) {
        if (ratingStr == null || ratingStr.isEmpty() || ratingStr.equals("-")) {
            return null;
        }
        try {
            return Integer.parseInt(ratingStr.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractPlayerId(Element row) {
        Element link = getLinkElement(row);
        if (link == null) return null;

        String href = link.attr("href");
        if (href.contains("id=")) {
            Matcher m = ID_PATTERN.matcher(href);
            return m.find() ? m.group(1) : null;
        }
        // Для URL вида /players/12345
        String[] parts = href.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : null;
    }
}
