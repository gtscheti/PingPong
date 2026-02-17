package org.pingpong.model;

import jakarta.persistence.*;
import lombok.*;
import org.pingpong.service.player.parser.PlayerParser;
import org.pingpong.service.player.parser.RttfPlayerParser;
import org.pingpong.service.player.parser.TtwPlayerParser;
import org.pingpong.Utils;
import org.pingpong.service.PlayerStatsService;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "PLAYERS")
public class Player {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fio;
    private String rttfId;
    private String ttwId;
    private Integer rttfRating;
    private Integer ttwRating;

    @OneToMany(mappedBy = "player", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Tournament> tournamentList = new ArrayList<>();

    @Transient
    private PlayerStats stats;  // не сохраняется в БД

    // Геттер и сеттер
    public PlayerStats getStats() {
        if (stats == null && tournamentList != null) {
            stats = new PlayerStatsService().calculate(this);
        }
        return stats;
    }

    public LocalDate getMaxDate() {
        return stats != null ? stats.getLastGameDate() : LocalDate.MIN;
    }

    public int getTotalTours() {
        return stats != null ? stats.getTotalTours() : 0;
    }

    public int getRttfTours() {
        return stats != null ? stats.getRttfTours() : 0;
    }

    public int getTtwTours() {
        return stats != null ? stats.getTtwTours() : 0;
    }

    public int getRttfWins() {
        return stats != null ? stats.getRttfWins() : 0;
    }

    public int getRttfLosses() {
        return stats != null ? stats.getRttfLosses() : 0;
    }

    public int getRttfGames() {
        return stats != null ? (stats.getRttfWins() + stats.getRttfLosses()) : 0;
    }

    public int getTtwWins() {
        return stats != null ? stats.getTtwWins() : 0;
    }

    public int getTtwLosses() {
        return stats != null ? stats.getTtwLosses() : 0;
    }

    public int getTtwGames() {
        return stats != null ? (stats.getTtwWins() + stats.getTtwLosses()) : 0;
    }

    public int getTotalWins() {
        return stats != null ? stats.getTotalWins() : 0;
    }

    public int getTotalLosses() {
        return stats != null ? stats.getTotalLosses() : 0;
    }

    public int getFirstPlaces() {
        return stats != null ? stats.getFirstPlaces() : 0;
    }

    public int getSecondPlaces() {
        return stats != null ? stats.getSecondPlaces() : 0;
    }

    public int getThirdPlaces() {
        return stats != null ? stats.getThirdPlaces() : 0;
    }

    public int getTotalGames() {
        return stats != null ? stats.getTotalWins() + stats.getTotalLosses() : 0;
    }

    public double getRttfWinRate() {
        int wins = getRttfWins();
        int losses = getRttfLosses();
        int total = wins + losses;
        return total > 0 ? (double) wins / total * 100 : 0.0;
    }

    public double getTtwWinRate() {
        int wins = getTtwWins();
        int losses = getTtwLosses();
        int total = wins + losses;
        return total > 0 ? (double) wins / total * 100 : 0.0;
    }

    public double getTotalWinRate() {
        int wins = getTotalWins();
        int losses = getTotalLosses();
        int total = wins + losses;
        return total > 0 ? (double) wins / total * 100 : 0.0;
    }

    // Опционально: форматированные строки для отображения
    public String getRttfWinRateFormatted() {
        return String.format("%.1f%%", getRttfWinRate());
    }

    public String getTtwWinRateFormatted() {
        return String.format("%.1f%%", getTtwWinRate());
    }

    public String getTotalWinRateFormatted() {
        return String.format("%.1f%%", getTotalWinRate());
    }

    public void refreshStats() {
        this.stats = new PlayerStatsService().calculate(this);
    }


    public void addTournament(Tournament tournament) {
        if (tournamentList == null) {
            tournamentList = new ArrayList<>();
        }
        if (!tournamentList.contains(tournament)) {
            tournamentList.add(tournament);
            tournament.setPlayer(this);
        }
    }

    @Override
    public String toString() {
        return fio.toUpperCase() +
                ", RTTW=" + rttfRating +
                ", TTW=" + ttwRating;
    }

    public List<Tournament> ParsePlayerTournaments(LocalDate dateFrom) throws IOException {
        PlayerParser parser = new RttfPlayerParser();
        List<Tournament> rttfTournaments = List.of();
        List<Tournament> ttwTournaments = List.of();

        //Загрузка данных RTTF;
        var ratingWithResults = parser.getRatingWithResults(this);
        if (ratingWithResults != null) {
            this.setRttfRating(ratingWithResults.first().getGameOrder());
            this.setFio(ratingWithResults.first().getOpponentName());
            rttfTournaments = parser.getTournaments(this, ratingWithResults.second(), dateFrom);
        }
        //Загрузка данных TTW;
        parser = new TtwPlayerParser();
        ratingWithResults = parser.getRatingWithResults(this);
        if (ratingWithResults != null) {
            this.setTtwRating(ratingWithResults.first().getGameOrder());
            ttwTournaments = parser.getTournaments(this, ratingWithResults.second(), dateFrom);
        }

        //Объединение данных;
        List<Tournament> merged = mergeTournaments(rttfTournaments, ttwTournaments);

        for (Tournament tournament : merged) {
            this.addTournament(tournament);
        }
        return this.getTournamentList();
    }

    public static List<Tournament> mergeTournaments(List<Tournament> existingList, List<Tournament> newList) {
        List<Tournament> result = new ArrayList<>(existingList);

        for (Tournament newTournament : newList) {
            Tournament matchingTournament = null;

            // Ищем совпадающий турнир
            for (Tournament existing : existingList) {
                if (tournamentsMatchByDateAndGames(existing, newTournament)) {
                    matchingTournament = existing;
                    break;
                }
            }

            if (matchingTournament != null) {
                // Совпадение — объединяем игры
                mergeTournamentGames(matchingTournament, newTournament);
            } else {
                // Нет совпадения — добавляем новый
                result.add(newTournament);
            }
        }

        return result;
    }

    private static boolean tournamentsMatchByDateAndGames(Tournament t1, Tournament t2) {
        // 1. Совпадает дата
        if (!Objects.equals(t1.getDate(), t2.getDate())) {
            return false;
        }

        List<Game> games1 = t1.getGames();
        List<Game> games2 = t2.getGames();

        if (games1 == null || games2 == null || games1.isEmpty() || games2.isEmpty()) {
            return false;
        }

        // 2. Считаем совпадающие игры (≥5)
        Set<String> gameKeys1 = games1.stream()
                .map(g -> normalizeGameKey(g.getOpponentName(), g.getScore(), g.getOpponentScore()))
                .collect(Collectors.toSet());

        long matchingGames = games2.stream()
                .map(g -> normalizeGameKey(g.getOpponentName(), g.getScore(), g.getOpponentScore()))
                .filter(gameKeys1::contains)
                .count();

        return matchingGames >= 5;
    }

    public static void mergeTournamentGames(Tournament existing, Tournament updated) {
        // Обновляем TTW поля турнира
        if (updated.getTtwId() != null) existing.setTtwId(updated.getTtwId());
        if (updated.getTtwName() != null) existing.setTtwName(updated.getTtwName());
        if (updated.getTtwDelta() != null) existing.setTtwDelta(updated.getTtwDelta());

        // Группируем игры по (shortenFio(opponentName) + gameOrder)
        Map<String, List<Game>> existingGamesByKey = groupGamesByKey(existing.getGames());
        Map<String, List<Game>> updatedGamesByKey = groupGamesByKey(updated.getGames());

        // Объединяем игры
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(existingGamesByKey.keySet());
        allKeys.addAll(updatedGamesByKey.keySet());

        List<Game> mergedGames = new ArrayList<>();

        for (String key : allKeys) {
            List<Game> existingGames = existingGamesByKey.getOrDefault(key, Collections.emptyList());
            List<Game> updatedGames = updatedGamesByKey.getOrDefault(key, Collections.emptyList());

            // Создаём объединённый список игр
            List<Game> mergedGamesByOrder = mergeGamesByGameOrder(existingGames, updatedGames);
            for (Game game : mergedGamesByOrder) {
                game.setTournament(existing);  // bidirectional: child -> parent
            }
            mergedGames.addAll(mergedGamesByOrder);
        }

        // Сортируем по gameOrder и присваиваем новые порядковые номера
        existing.setGames(sortAndRenumberGames(mergedGames));
    }

    private static Map<String, List<Game>> groupGamesByKey(List<Game> games) {
        return games.stream()
                .collect(Collectors.groupingBy(
                        g -> Utils.shortenFio(g.getOpponentName()),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    // Сортируем по gameOrder и нумеруем
                                    AtomicInteger counter = new AtomicInteger(1);
                                    return list.stream()
                                            .sorted(Comparator.comparingInt(Game::getGameOrder))
                                            .peek(game -> game.setGameOrder(counter.getAndIncrement()))
                                            .collect(Collectors.toList());
                                }
                        )
                ));
    }

    private static List<Game> mergeGamesByGameOrder(List<Game> existingGames, List<Game> updatedGames) {
        // Создаем карту existing игр по gameOrder
        Map<Integer, Game> existingByOrder = existingGames.stream()
                .collect(Collectors.toMap(Game::getGameOrder, game -> game, (g1, g2) -> g1));

        // Создаем карту updated игр по gameOrder
        Map<Integer, Game> updatedByOrder = updatedGames.stream()
                .collect(Collectors.toMap(Game::getGameOrder, game -> game, (g1, g2) -> g1));

        // Собираем все уникальные gameOrder
        Set<Integer> allGameOrders = new HashSet<>();
        allGameOrders.addAll(existingByOrder.keySet());
        allGameOrders.addAll(updatedByOrder.keySet());

        List<Game> result = new ArrayList<>();

        for (Integer gameOrder : allGameOrders) {
            Game existing = existingByOrder.get(gameOrder);
            Game updated = updatedByOrder.get(gameOrder);

            Game merged;

            if (existing != null && updated != null) {
                // Есть в обоих - объединяем
                merged = new Game();
                merged.setGameOrder(gameOrder);
                merged.setGameNaturalOrder(existing.getGameNaturalOrder() != null ?
                        existing.getGameNaturalOrder() : updated.getGameNaturalOrder());
                merged.setOpponentName(existing.getOpponentName()); // берем из RTTF или TTW

                // RTTF параметры из existing
                merged.setOpponentRttfRating(existing.getOpponentRttfRating());
                merged.setRttfDelta(existing.getRttfDelta());
                merged.setOpponentScore(existing.getOpponentScore());
                merged.setScore(existing.getScore());

                // TTW параметры из updated
                merged.setOpponentTtwRating(updated.getOpponentTtwRating());
                merged.setTtwDelta(updated.getTtwDelta());

            } else if (existing != null) {
                // Только в existing - копируем
                merged = cloneGame(existing); // или new Game() + копирование полей
            } else {
                // Только в updated - копируем
                merged = cloneGame(updated);
            }

            result.add(merged);
        }

        // Сортируем по gameOrder
        result.sort(Comparator.comparing(Game::getGameOrder));

        return result;
    }

    private static Game cloneGame(Game source) {
        Game clone = new Game();
        clone.setGameNaturalOrder(source.getGameNaturalOrder());
        clone.setGameOrder(source.getGameOrder());
        clone.setOpponentName(source.getOpponentName());
        clone.setOpponentRttfRating(source.getOpponentRttfRating());
        clone.setRttfDelta(source.getRttfDelta());
        clone.setOpponentScore(source.getOpponentScore());
        clone.setScore(source.getScore());
        clone.setOpponentTtwRating(source.getOpponentTtwRating());
        clone.setTtwDelta(source.getTtwDelta());
        return clone;
    }

    private static String normalizeGameKey(String opponentName, Integer score, Integer opponentScore) {
        return Utils.shortenFio(opponentName) + "|" + score + "|" + opponentScore;
    }

    private static List<Game> sortAndRenumberGames(List<Game> games) {
        // Сортируем по opponentName, затем по исходному gameOrder
        List<Game> sorted = games.stream()
                .sorted(Comparator
                        .comparing(Game::getOpponentName)
                        .thenComparing(Game::getGameOrder))
                .collect(Collectors.toList());

        // Переприсваиваем gameOrder с 1
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setGameOrder(i + 1);
        }

        return sorted;
    }

    public String getIdentifier(String source) {
        return switch (source) {
            case "RTTF" -> getRttfId();
            case "TTW" -> getTtwId();
            default -> null;
        };
    }
}
