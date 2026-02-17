package org.pingpong;

import org.pingpong.model.Game;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class Utils {
    public record Pair<L, R>(L first, R second) {}

    public static List<Game> sortAndRenumberGames(List<Game> games) {
        if (games == null || games.isEmpty()) {
            return games;
        }

        List<Game> sorted = games.stream()
                .sorted(Comparator
                        .comparing(Game::getOpponentName, Comparator.nullsLast(String::compareTo)) // null-безопасно
                        .thenComparing(Game::getGameOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                )
                .collect(Collectors.toList());

        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setGameOrder(i + 1);
        }

        return sorted;
    }

    public static String shortenFio(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "";
        }

        String[] parts = fullName.replace("ё", "е").trim().split("\\s+");
        if (parts.length == 0) return "";

        // Фамилия целиком (первое слово)
        String surname = parts[0];

        // Инициалы от остальных частей (имя, отчество)
        StringBuilder initials = new StringBuilder();

        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                initials.append(parts[i].charAt(0)).append(".");
            }
        }

        // Собираем: Фамилия И.О.
        return surname + " " + initials.toString().trim();
    }
}
