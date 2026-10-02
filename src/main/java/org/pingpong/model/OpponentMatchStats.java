package org.pingpong.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Детальная статистика матчей с конкретным соперником.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpponentMatchStats {
    private String opponentName;
    private int totalGames;
    private int wins;
    private int losses;
    private int wonPoints;
    private int lostPoints;
    private BigDecimal totalRttfDelta;
    private BigDecimal totalTtwDelta;

    /**
     * Процент побед.
     */
    public double getWinRate() {
        return totalGames > 0 ? (double) wins / totalGames * 100 : 0.0;
    }

    /**
     * Форматированный процент побед.
     */
    public String getWinRateFormatted() {
        return String.format("%.1f%%", getWinRate());
    }

    /**
     * Форматированная дельта RTTF.
     */
    public String getRttfDeltaFormatted() {
        return totalRttfDelta != null ? String.format("%.2f", totalRttfDelta) : "0.00";
    }

    /**
     * Форматированная дельта TTW.
     */
    public String getTtwDeltaFormatted() {
        return totalTtwDelta != null ? String.format("%.2f", totalTtwDelta) : "0.00";
    }

    /**
     * Баланс очков (выигранные - проигранные).
     */
    public int getPointsDifference() {
        return wonPoints - lostPoints;
    }
}