package org.pingpong.service;

import org.pingpong.model.Game;
import org.pingpong.model.Player;
import org.pingpong.model.PlayerStats;
import org.pingpong.model.Tournament;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class PlayerStatsService {

    public PlayerStats calculate(Player player) {
        PlayerStats stats = new PlayerStats();
        List<Tournament> tournaments = player.getTournamentList();

        if (tournaments == null || tournaments.isEmpty()) {
            return stats;
        }

        LocalDate lastGameDate = null;

        for (Tournament tournament : tournaments) {
            updateTournamentCount(stats, tournament);
            lastGameDate = updateLastGameDate(lastGameDate, tournament.getDate());
            updatePlaces(stats, tournament.getPlace());

            boolean isRttf = isRttfTournament(tournament);
            boolean isTtw = isTtwTournament(tournament);

            for (Game game : tournament.getGames()) {
                updateWinLossStats(stats, game, isRttf, isTtw);
            }
        }

        stats.setLastGameDate(lastGameDate != null ? lastGameDate : LocalDate.MIN);
        return stats;
    }

    private void updateTournamentCount(PlayerStats stats, Tournament tournament) {
        stats.setTotalTours(stats.getTotalTours() + 1);
        if (tournament.getRttfName() != null && !tournament.getRttfName().trim().isEmpty()) {
            stats.setRttfTours(stats.getRttfTours() + 1);
        }
        if (tournament.getTtwName() != null && !tournament.getTtwName().trim().isEmpty()) {
            stats.setTtwTours(stats.getTtwTours() + 1);
        }
    }

    private LocalDate updateLastGameDate(LocalDate currentMax, LocalDate newDate) {
        if (newDate == null) return currentMax;
        return currentMax == null || newDate.isAfter(currentMax) ? newDate : currentMax;
    }

    private void updatePlaces(PlayerStats stats, Integer place) {
        if (place == null) return;
        switch (place) {
            case 1 -> stats.setFirstPlaces(stats.getFirstPlaces() + 1);
            case 2 -> stats.setSecondPlaces(stats.getSecondPlaces() + 1);
            case 3 -> stats.setThirdPlaces(stats.getThirdPlaces() + 1);
        }
    }

    private boolean isRttfTournament(Tournament tournament) {
        return tournament.getRttfName() != null && !tournament.getRttfName().trim().isEmpty();
    }

    private boolean isTtwTournament(Tournament tournament) {
        return tournament.getTtwName() != null && !tournament.getTtwName().trim().isEmpty();
    }

    private void updateWinLossStats(PlayerStats stats, Game game, boolean isRttf, boolean isTtw) {
        Integer playerScore = game.getScore();
        Integer opponentScore = game.getOpponentScore();

        // Защита от null
        if (playerScore == null || opponentScore == null) return;

        boolean win = playerScore > opponentScore;
        boolean loss = playerScore < opponentScore;

        if (win) {
            stats.setTotalWins(stats.getTotalWins() + 1);
            if (isRttf && Objects.nonNull(game.getRttfDelta())) {
                stats.setRttfWins(stats.getRttfWins() + 1);
            }
            if (isTtw && Objects.nonNull(game.getTtwDelta())) {
                stats.setTtwWins(stats.getTtwWins() + 1);
            }
        } else if (loss) {
            stats.setTotalLosses(stats.getTotalLosses() + 1);
            if (isRttf && Objects.nonNull(game.getRttfDelta())) {
                stats.setRttfLosses(stats.getRttfLosses() + 1);
            }
            if (isTtw && Objects.nonNull(game.getTtwDelta())) {
                stats.setTtwLosses(stats.getTtwLosses() + 1);
            }
        }
    }
}