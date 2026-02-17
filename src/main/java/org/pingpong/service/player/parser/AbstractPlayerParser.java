package org.pingpong.service.player.parser;

import org.pingpong.Utils;
import org.pingpong.model.Game;
import org.pingpong.model.GameKey;
import org.pingpong.model.Player;
import org.pingpong.model.Tournament;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.pingpong.Utils.Pair;

public abstract class AbstractPlayerParser implements PlayerParser {

    @Override
    public Pair<GameKey, Element> getRatingWithResults(Player player) throws IOException {
        Document doc = connectToProfile(player);
        if (doc == null) return null;

        Element profileSection = extractProfileSection(doc);
        if (profileSection == null) {
            throw new RuntimeException("Не найден блок профиля игрока на сайте: " + getSiteName());
        }

        Integer rating = parseRating(profileSection);
        String fio = extractFio(profileSection);
        GameKey playerData = new GameKey(rating, fio);

        Element resultsTable = extractResultsTable(doc);
        return new Pair<>(playerData, resultsTable);
    }

    @Override
    public List<Tournament> getTournaments(Player player, Element playerResults, LocalDate dateFrom) throws IOException {
        if (playerResults == null) return List.of();
        return parseTournaments(playerResults, player.getIdentifier(getSiteName()), dateFrom);
    }

    // Абстрактные методы — специфичны для каждого сайта
    protected abstract Document connectToProfile(Player player) throws IOException;

    protected abstract Element extractProfileSection(Document doc);

    protected abstract Integer parseRating(Element profileSection);

    protected abstract String extractFio(Element profileSection);

    protected abstract Element extractResultsTable(Document doc);

    protected abstract String getSiteName(); // например "RTTF" или "TTW"

    // Для getTournaments
    protected abstract List<Tournament> parseTournaments(Element results, String playerId, LocalDate dateFrom) throws IOException;

    // Общая логика сортировки и нумерации игр
    protected List<Game> postProcessGames(List<Game> games) {
        return Utils.sortAndRenumberGames(games);
    }
}