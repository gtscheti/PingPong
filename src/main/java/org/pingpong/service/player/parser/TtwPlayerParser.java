package org.pingpong.service.player.parser;

import org.pingpong.SiteConfig;
import org.pingpong.Utils;
import org.pingpong.model.Game;
import org.pingpong.model.Player;
import org.pingpong.model.Tournament;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TtwPlayerParser extends AbstractPlayerParser {

    private final SiteConfig config = SiteConfig.TTW;
    private static final SiteConfig configTour = SiteConfig.TTWTOUR;

    @Override
    protected Document connectToProfile(Player player) throws IOException {
        if (player.getTtwId() == null) return null;
        return config.connect(player.getTtwId()).get();
    }

    @Override
    protected Element extractProfileSection(Document doc) {
        return doc.selectFirst("div.layout-row.player-page");
    }

    @Override
    protected Integer parseRating(Element profileSection) {
        Element rating = profileSection.selectFirst("div.header-rating");
        return rating != null ? Integer.valueOf(rating.text()) : null;
    }

    @Override
    protected String extractFio(Element profileSection) {
        return Objects.requireNonNull(profileSection.selectFirst("h1")).text();
    }

    @Override
    protected Element extractResultsTable(Document doc) {
        return doc.selectFirst("div.player-all-games > table > tbody");
    }

    @Override
    protected String getSiteName() {
        return "TTW";
    }

    @Override
    protected List<Tournament> parseTournaments(Element results, String playerId, LocalDate dateFrom) throws IOException {
        Elements rows = results.select("tr");
        List<Tournament> tournaments = new ArrayList<>();

        for (Element row : rows) {
            Element infoCell = row.selectFirst("td.game-tournament-name-cell");
            if (infoCell != null) {
                Element nextTd = infoCell.nextElementSibling();
                var ttwDelta = (nextTd != null && nextTd.text().isEmpty()) ?
                        BigDecimal.ZERO : new BigDecimal(nextTd.text());

                Tournament tournament = getTournamentInfo(infoCell).withTtwDelta(ttwDelta);
                if (tournament.getDate().isBefore(dateFrom) || tournament.getDate().equals(dateFrom)) break;

                Element nextRow = row.nextElementSibling();
                List<Game> games = getGames(nextRow);
                games = postProcessGames(games);

                for (Game game : games) {
                    tournament.addGame(game);
                }
                tournaments.add(tournament);
            }
        }
        return tournaments;
    }

    @Override
    public Tournament getTournamentInfo(Element tr) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        String dateStr = tr.ownText().trim().replace(",", "");
        Element link = tr.selectFirst("a");
        String id = null;
        if (link != null) {
            id = link.attr("href").substring(link.attr("href").lastIndexOf("=") + 1);
        }
        var name = Objects.requireNonNull(link).text().trim();
        LocalDate date = LocalDate.parse(dateStr, formatter);

        return Tournament.builder()
                .ttwId(id)
                .ttwName(name)
                .date(date)
                .build();
    }

    @Override
    public List<Game> getGames(Element element) {
        List<Game> games = new ArrayList<>();
        Element current = element;
        int order = 1;

        while (current != null && !current.select("td.game-score-cell").isEmpty()) {
            Game game = getMatchData(current);
            if (game != null) {
                games.add(game.withGameNaturalOrder(order).withGameOrder(order++));
            }
            current = current.nextElementSibling();
        }
        return games;
    }

    private Game getMatchData(Element tr) {
        String scoreText = tr.select("td.game-score-cell").text();
        if (!Character.isDigit(scoreText.charAt(0))) return null;

        String info = tr.select("td.game-name-cell").text();
        Pattern pattern = Pattern.compile("(.+)\\s*\\(([^)]+)\\)");
        Matcher matcher = pattern.matcher(info);

        String fullName = "";
        long rating = 0;
        if (matcher.matches()) {
            fullName = Utils.shortenFio(matcher.group(1).trim());
            rating = Math.round(Double.parseDouble(matcher.group(2)));
        }

        String deltaText = tr.select("td.game-delta-cell").text();
        String[] scoreParts = scoreText.split(":");
        BigDecimal delta = deltaText.isEmpty() ? BigDecimal.ZERO : new BigDecimal(deltaText);

        return Game.builder()
                .opponentName(fullName)
                .score(Integer.parseInt(scoreParts[0]))
                .opponentScore(Integer.parseInt(scoreParts[1]))
                .opponentTtwRating(Math.toIntExact(rating))
                .ttwDelta(delta)
                .build();
    }

    public static Integer getTournamentPlace(Tournament tournament, String fio) throws IOException {
        String shortenedFio = Utils.shortenFio(fio);
        Document doc = configTour.connect(tournament.getTtwId()).get();
        Element tbody = doc.selectFirst("div.tournament-players > table > tbody");
        if (tbody == null) return 0;

        for (Element row : tbody.select("tr")) {
            Elements cells = row.select("td");
            if (cells.size() >= 2) {
                String placeStr = cells.get(0).text();
                String fullName = cells.get(1).select("a").text();
                if (Utils.shortenFio(fullName).equals(shortenedFio)) {
                    return Integer.valueOf(placeStr);
                }
            }
        }
        return 0;
    }
}