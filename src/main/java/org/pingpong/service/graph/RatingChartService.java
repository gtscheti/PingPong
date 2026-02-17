package org.pingpong.service.graph;

import org.pingpong.model.Player;
import org.pingpong.model.Tournament;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RatingChartService {

    private static final LocalDate START_DATE = LocalDate.of(2000, 1, 1);

    public RatingChartData buildChartData(List<Tournament> tournaments, Player player) {
        if (tournaments == null || player == null) {
            throw new IllegalArgumentException("Tournaments and player must not be null");
        }

        Map<LocalDate, BigDecimal> rttfDeltas = getDeltaMap(tournaments, Tournament::getRttfDelta);
        Map<LocalDate, BigDecimal> ttwDeltas = getDeltaMap(tournaments, Tournament::getTtwDelta);

        List<LocalDate> allDates = mergeSortedUniqueDates(rttfDeltas.keySet(), ttwDeltas.keySet());

        List<BigDecimal> rttfRatings = computeRatingHistory(allDates, player.getRttfRating(), rttfDeltas);
        List<BigDecimal> ttwRatings = computeRatingHistory(allDates, player.getTtwRating(), ttwDeltas);

        // Поиск максимумов
        var maxRttfData = findMax(rttfRatings, allDates);
        var maxTtwData = findMax(ttwRatings, allDates);

        return new RatingChartData(
                allDates,
                rttfRatings,
                ttwRatings,
                maxRttfData.rating(),
                maxRttfData.date(),
                maxTtwData.rating(),
                maxTtwData.date()
        );
    }

    private Map<LocalDate, BigDecimal> getDeltaMap(List<Tournament> tournaments,
                                                   Function<Tournament, BigDecimal> deltaExtractor) {
        return tournaments.stream()
                .filter(t -> deltaExtractor.apply(t) != null)
                .collect(Collectors.groupingBy(Tournament::getDate,
                        Collectors.reducing(BigDecimal.ZERO, deltaExtractor, BigDecimal::add)));
    }

    private List<LocalDate> mergeSortedUniqueDates(Set<LocalDate> dates1, Set<LocalDate> dates2) {
        return Stream.concat(Stream.concat(dates1.stream(), dates2.stream()), Stream.of(START_DATE))
                .distinct()
                .sorted()
                .toList();
    }

    private List<BigDecimal> computeRatingHistory(List<LocalDate> dates, Integer currentRating,
                                                  Map<LocalDate, BigDecimal> deltas) {
        if (currentRating == null || currentRating == 0) {
            return List.of();
        }

        List<BigDecimal> ratings = new ArrayList<>(dates.size());
        BigDecimal rating = BigDecimal.valueOf(currentRating);
        ratings.add(rating);

        for (int i = dates.size() - 1; i > 0; i--) {
            LocalDate date = dates.get(i);
            BigDecimal delta = deltas.getOrDefault(date, BigDecimal.ZERO);
            rating = rating.subtract(delta);
            ratings.add(rating); // собираем в обратном порядке
        }
        Collections.reverse(ratings); // теперь правильный порядок
        return ratings;
    }

    private MaxData findMax(List<BigDecimal> ratings, List<LocalDate> dates) {
        if (ratings.isEmpty()) {
            return new MaxData(BigDecimal.ZERO, null);
        }
        BigDecimal max = BigDecimal.ZERO;
        LocalDate maxDate = null;
        for (int i = 0; i < ratings.size(); i++) {
            if (ratings.get(i).compareTo(max) > 0) {
                max = ratings.get(i);
                maxDate = dates.get(i);
            }
        }
        return new MaxData(max, maxDate);
    }

    private record MaxData(BigDecimal rating, LocalDate date) {}
}