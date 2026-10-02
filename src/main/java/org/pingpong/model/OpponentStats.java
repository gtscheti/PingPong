package org.pingpong.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Упрощенная статистика игр с конкретным соперником.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpponentStats {
    private String name;
    private int totalGames;
    private int wins;
    private int losses;
    private double winRate;

    /**
     * Процент побед в строковом формате.
     */
    public String getWinRateFormatted() {
        return String.format("%.1f%%", winRate);
    }

    /**
     * Проверка корректности статистики.
     */
    public boolean isStatsValid() {
        return totalGames == wins + losses;
    }
}