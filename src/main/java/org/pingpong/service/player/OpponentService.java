package org.pingpong.service.player;

import org.pingpong.model.OpponentMatchStats;
import org.pingpong.model.OpponentStats;
import org.pingpong.model.Player;
import org.pingpong.model.Tournament;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для подсчета статистики игр игрока с различными соперниками.
 */
@Service
public class OpponentService {

    /**
     * Получает детальную статистику всех соперников выбранного игрока.
     *
     * @param player игрок, чью статистику нужно рассчитать
     * @return список статистик по всем соперникам, отсортированный по количеству игр (по убыванию)
     */
    public List<OpponentMatchStats> getAllOpponentsMatchStats(Player player) {
        if (player == null || player.getTournamentList() == null || player.getTournamentList().isEmpty()) {
            return Collections.emptyList();
        }

        // Карта: соперник -> [игры, победы, поражения, выигр.очки, проигр.очки]
        Map<String, int[]> opponentMap = new LinkedHashMap<>();
        // Карта: соперник -> [дельта RTTF, дельта TTW]
        Map<String, BigDecimal[]> deltaMap = new LinkedHashMap<>();

        for (Tournament tournament : player.getTournamentList()) {
            if (tournament.getGames() == null || tournament.getGames().isEmpty()) {
                continue;
            }

            for (org.pingpong.model.Game game : tournament.getGames()) {
                String opponentName = game.getOpponentName();
                if (opponentName == null || opponentName.trim().isEmpty()) {
                    continue;
                }

                // Инициализируем статистику, если соперник впервые
                opponentMap.computeIfAbsent(opponentName, k -> new int[5]);
                deltaMap.computeIfAbsent(opponentName, k -> new BigDecimal[2]);

                int[] stats = opponentMap.get(opponentName);
                BigDecimal[] deltas = deltaMap.get(opponentName);

                // Увеличиваем счет игр
                stats[0]++;

                // Считаем очки
                int playerScore = game.getScore() != null ? game.getScore() : 0;
                int opponentScore = game.getOpponentScore() != null ? game.getOpponentScore() : 0;

                stats[3] += playerScore;
                stats[4] += opponentScore;

                // Определяем результат игры
                if (playerScore > opponentScore) {
                    stats[1]++; // победа
                } else {
                    stats[2]++; // поражение
                }

                // Суммируем дельты
                BigDecimal rttfDelta = game.getRttfDelta() != null ? game.getRttfDelta() : BigDecimal.ZERO;
                BigDecimal ttwDelta = game.getTtwDelta() != null ? game.getTtwDelta() : BigDecimal.ZERO;

                deltas[0] = (deltas[0] != null ? deltas[0] : BigDecimal.ZERO).add(rttfDelta);
                deltas[1] = (deltas[1] != null ? deltas[1] : BigDecimal.ZERO).add(ttwDelta);
            }
        }

        // Преобразуем карты в список объектов OpponentMatchStats
        return opponentMap.entrySet().stream()
                .map(entry -> {
                    String name = entry.getKey();
                    int[] stats = entry.getValue();
                    BigDecimal[] deltas = deltaMap.get(name);

                    int games = stats[0];
                    int wins = stats[1];
                    int losses = stats[2];
                    int wonPoints = stats[3];
                    int lostPoints = stats[4];
                    BigDecimal totalRttfDelta = deltas[0] != null ? deltas[0] : BigDecimal.ZERO;
                    BigDecimal totalTtwDelta = deltas[1] != null ? deltas[1] : BigDecimal.ZERO;

                    return OpponentMatchStats.builder()
                            .opponentName(name)
                            .totalGames(games)
                            .wins(wins)
                            .losses(losses)
                            .wonPoints(wonPoints)
                            .lostPoints(lostPoints)
                            .totalRttfDelta(totalRttfDelta)
                            .totalTtwDelta(totalTtwDelta)
                            .build();
                })
                .sorted(
                        Comparator.comparingInt(OpponentMatchStats::getTotalGames)
                                .reversed()
                                .thenComparing(
                                        Comparator.comparingInt(OpponentMatchStats::getWins)
                                                .reversed()
                                )
                )
                .collect(Collectors.toList());
    }


    /**
     * Получает топ-N наиболее частых соперников (упрощенная статистика).
     *
     * @param player   игрок
     * @param topCount количество необходимых соперников
     * @return список статистик по топ-N соперникам
     */
    public List<OpponentStats> getTopOpponents(Player player, int topCount) {
        List<OpponentMatchStats> allOpponents = getAllOpponentsMatchStats(player);
        if (allOpponents.isEmpty()) {
            return Collections.emptyList();
        }

        int limit = Math.min(topCount, allOpponents.size());
        return allOpponents.subList(0, limit).stream()
                .map(stats -> OpponentStats.builder()
                        .name(stats.getOpponentName())
                        .totalGames(stats.getTotalGames())
                        .wins(stats.getWins())
                        .losses(stats.getLosses())
                        .winRate(stats.getWinRate())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Получает общее количество уникальных соперников.
     *
     * @param player игрок
     * @return количество уникальных соперников
     */
    public int getUniqueOpponentsCount(Player player) {
        return getAllOpponentsMatchStats(player).size();
    }
}