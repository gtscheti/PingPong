package org.example.model;

import lombok.Data;
import lombok.Getter;

import java.time.LocalDate;

@Data
@Getter
public class PlayerStats {
    public int totalTours = 0;
    private int rttfTours = 0;
    private int ttwTours = 0;
    private int rttfWins = 0;
    private int rttfLosses = 0;
    private int ttwWins = 0;
    private int ttwLosses = 0;
    private int totalWins = 0;
    private int totalLosses = 0;
    private int firstPlaces = 0;
    private int secondPlaces = 0;
    private int thirdPlaces = 0;
    private LocalDate lastGameDate;

    public int getTotalGames() {
        return totalWins + totalLosses;
    }

    // toString оставлен как есть, но без @Data
    @Override
    public String toString() {
        return String.format("Турниры: %d | Игры: %d (+%d -%d) | RTTF: +%d -%d | TTW: +%d -%d",
                totalTours,
                getTotalGames(),
                totalWins, totalLosses,
                rttfWins, rttfLosses,
                ttwWins, ttwLosses);
    }
}