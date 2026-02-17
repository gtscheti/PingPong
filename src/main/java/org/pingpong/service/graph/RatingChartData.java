package org.pingpong.service.graph;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RatingChartData(
        List<LocalDate> dates,
        List<BigDecimal> rttfRatings,
        List<BigDecimal> ttwRatings,
        BigDecimal maxRttf,
        LocalDate maxRttfDate,
        BigDecimal maxTtw,
        LocalDate maxTtwDate
) {}