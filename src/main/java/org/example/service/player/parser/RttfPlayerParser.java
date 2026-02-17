package org.example.service.player.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.SiteConfig;
import org.example.Utils;
import org.example.model.Game;
import org.example.model.Player;
import org.example.model.Tournament;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RttfPlayerParser extends AbstractPlayerParser {

    private static final Logger log = LoggerFactory.getLogger(RttfPlayerParser.class);

    private final SiteConfig config = SiteConfig.RTTF;

    @Override
    protected Document connectToProfile(Player player) throws IOException {
        if (player.getRttfId() == null) return null;
        return config.connect(player.getRttfId()).get();
    }

    @Override
    protected Element extractProfileSection(Document doc) {
        return doc.selectFirst("section.player-info");
    }

    @Override
    protected Integer parseRating(Element profileSection) {
        Element rating = profileSection.selectFirst("dfn");
        return rating != null ? Integer.valueOf(rating.text()) : null;
    }

    @Override
    protected String extractFio(Element profileSection) {
        return Objects.requireNonNull(profileSection.selectFirst("h1")).text();
    }

    @Override
    protected Element extractResultsTable(Document doc) {
        return doc.selectFirst("section.player-results-all > table > tbody");
    }

    @Override
    protected String getSiteName() {
        return "RTTF";
    }

    @Override
    protected List<Tournament> parseTournaments(Element results, String playerId, LocalDate dateFrom) throws IOException {
        Elements rows = results.select("table.tablesort > tbody > tr");
        List<Tournament> tournaments = new ArrayList<>();

        for (Element row : rows) {
            Tournament tournament = getTournamentInfo(row);
            if (tournament.getDate().isBefore(dateFrom) || tournament.getDate().equals(dateFrom)) break;

            Element data = getRttfMatchesData(playerId, tournament.getRttfId());
            List<Game> games = getGames(data);
            games = postProcessGames(games);

            for (Game game : games) {
                tournament.addGame(game);
            }
            tournaments.add(tournament);
        }
        return tournaments;
    }

    @Override
    public Tournament getTournamentInfo(Element tr) {
        return Tournament.builder()
                .rttfId(getTournamentId(tr))
                .rttfName(getTournamentName(tr))
                .date(getTournamentDate(tr))
                .place(getTournamentPlace(tr))
                .rttfDelta(getTournamentDelta(tr))
                .build();
    }

    @Override
    public List<Game> getGames(Element tournament) {
        if (tournament == null) return List.of();
        Elements matches = tournament.select("table.tablesort > tbody > tr");
        List<Game> games = new ArrayList<>();
        int order = 1;
        for (Element match : matches) {
            String opponentName = Utils.shortenFio(getOpponentFio(match));
            if (!opponentName.isEmpty()) {
                Game game = getMatchData(match)
                        .withOpponentName(opponentName)
                        .withGameNaturalOrder(order)
                        .withGameOrder(order++);
                games.add(game);
            }
        }
        return games;
    }

    private String getTournamentId(Element tr) {
        String onclick = tr.attr("onclick");
        Matcher m = Pattern.compile("showTour\\((\\d+)").matcher(onclick);
        return m.find() ? m.group(1) : null;
    }

    private LocalDate getTournamentDate(Element tr) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        String text = Objects.requireNonNull(tr.select("td").first()).text();
        String dateStr = text.split(" ")[0];
        return LocalDate.parse(dateStr, formatter);
    }

    private String getTournamentName(Element tr) {
        Element td1 = tr.select("td").get(1);
        Element td2 = tr.select("td").get(2);
        return td2.text() + "-" + td1.text();
    }

    private Integer getTournamentPlace(Element tr) {
        try {
            Element td = tr.select("td").get(7);
            return Integer.valueOf(td.text().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private BigDecimal getTournamentDelta(Element tr) {
        try {
            var delta = tr.select("td").get(6).text().replace("−", "-");
            return delta.isEmpty() ? BigDecimal.ZERO : new BigDecimal(delta);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private Element getRttfMatchesData(String playerId, String rttfId) {
        try {
            Connection.Response response = Jsoup.connect("https://rttf.ru/?ajax=")
                    .data("showTour", rttfId)
                    .data("userID", playerId)
                    .userAgent("Mozilla/5.0")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .method(Connection.Method.POST)
                    .execute();

            Map<String, Object> data = new ObjectMapper().readValue(response.body(), Map.class);
            String html = (String) data.get("html");
            return Jsoup.parse(html);
        } catch (Exception e) {
            log.error("Ошибка при загрузке данных матчей для игрока {} и турнира {}: {}",
                    playerId, rttfId, e.getMessage(), e);
            return null;
        }
    }

    private String getOpponentFio(Element tr) {
        return tr.select("td").get(2).text();
    }

    private Game getMatchData(Element tr) {
        String rating = tr.select("td").get(3).text();
        String score = tr.select("td").get(4).text();
        String[] parts = score.split(" : ");
        String delta = tr.select("td").get(5).text().replace("−", "-");

        return Game.builder()
                .score(Integer.parseInt(parts[0]))
                .opponentScore(Integer.parseInt(parts[1]))
                .opponentRttfRating(Integer.parseInt(rating))
                .rttfDelta(delta.isEmpty() ? BigDecimal.ZERO : new BigDecimal(delta))
                .build();
    }
}