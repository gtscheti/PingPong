package org.pingpong.view;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.pingpong.model.PlayerMatch;
import org.pingpong.service.player.search.PlayerSearch;
import org.pingpong.service.player.search.RttfPlayerSearch;
import org.pingpong.service.player.search.TtwPlayerSearch;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class BatchSearchDialog extends Stage {

    private final TextArea inputArea = new TextArea();
    private final TextField cityField = new TextField();
    private final ListView<String> resultsView = new ListView<>();
    private final Label statusLabel = new Label("Готово");
    private final Button searchButton = new Button("Найти");
    private final Button sortButton = new Button("Сортировать по рейтингу ↓");
    private final Button copyButton = new Button("Скопировать список"); // Новая кнопка

    public BatchSearchDialog() {
        setTitle("Пакетный поиск игроков");
        initModality(Modality.APPLICATION_MODAL);
        setMinWidth(700);
        setMinHeight(600);

        RadioButton rttfRadio = new RadioButton("RTTF");
        RadioButton ttwRadio = new RadioButton("TTW");
        ToggleGroup group = new ToggleGroup();
        rttfRadio.setToggleGroup(group);
        ttwRadio.setToggleGroup(group);
        rttfRadio.setSelected(true);

        HBox radioBox = new HBox(10, rttfRadio, ttwRadio);
        radioBox.setPadding(new Insets(10));

        inputArea.setPromptText("Введите список игроков, например:\n1. Иванов Иван\n2. Петров Петр");
        inputArea.setPrefHeight(120);

        cityField.setPromptText("Город (опционально, например: Москва)");

        VBox inputBox = new VBox(5, new Label("Имена:"), inputArea, new Label("Город (фильтр):"), cityField);

        searchButton.setOnAction(e -> {
            PlayerSearch search = rttfRadio.isSelected() ? new RttfPlayerSearch() : new TtwPlayerSearch();
            performBatchSearch(search);
        });

        sortButton.setOnAction(e -> sortResults());

        copyButton.setOnAction(e -> copyResultsToClipboard()); // Обработчик для копирования
        copyButton.setDisable(true); // Отключаем, пока нет результатов

        HBox buttons = new HBox(10, searchButton, sortButton, copyButton);
        buttons.setPadding(new Insets(10, 0, 10, 0));

        VBox layout = new VBox(
                10,
                new Label("Выберите источник и вставьте список игроков:"),
                radioBox,
                inputBox,
                buttons,
                new Label("Результаты поиска:"),
                resultsView,
                statusLabel
        );
        layout.setPadding(new Insets(15));
        layout.setSpacing(8);

        Scene scene = new Scene(layout);
        setScene(scene);
    }

    private void performBatchSearch(PlayerSearch search) {
        String text = inputArea.getText().trim();
        if (text.isEmpty()) {
            statusLabel.setText("Введите список игроков");
            return;
        }

        List<String> names = extractNames(text);
        if (names.isEmpty()) {
            statusLabel.setText("Не найдены имена игроков");
            return;
        }

        String cityFilter = cityField.getText().trim();

        statusLabel.setText("Поиск...");
        searchButton.setDisable(true);
        sortButton.setDisable(true);
        copyButton.setDisable(true);

        CompletableFuture<List<String>> future = CompletableFuture.supplyAsync(() -> {
            List<String> results = new ArrayList<>();
            for (String name : names) {
                try {
                    List<PlayerMatch> found = search.searchByName(name.trim());
                    PlayerMatch selected;

                    if (found.size() > 1 && !cityFilter.isEmpty()) {
                        List<PlayerMatch> filteredByCity = found.stream()
                                .filter(p -> p.getCity() != null &&
                                        p.getCity().toLowerCase().contains(cityFilter.toLowerCase()))
                                .toList();

                        if (!filteredByCity.isEmpty()) {
                            selected = filteredByCity.stream()
                                    .max(Comparator.comparing(PlayerMatch::getRating, Comparator.nullsFirst(Integer::compareTo)))
                                    .orElse(filteredByCity.get(0));
                        } else {
                            selected = found.stream()
                                    .max(Comparator.comparing(PlayerMatch::getRating, Comparator.nullsFirst(Integer::compareTo)))
                                    .orElse(found.get(0));
                        }
                    } else if (!found.isEmpty()) {
                        selected = found.stream()
                                .max(Comparator.comparing(PlayerMatch::getRating, Comparator.nullsFirst(Integer::compareTo)))
                                .orElse(found.get(0));
                    } else {
                        selected = PlayerMatch.builder()
                                .fullName(name)
                                .rating(null)
                                .city("")
                                .playerId("")
                                .build();
                    }

                    String line = String.format("%s — %s — %s",
                            selected.getFullName(),
                            selected.getCity().isEmpty() ? "—" : selected.getCity(),
                            selected.getRating() == null ? "—" : selected.getRating());
                    results.add(line);
                } catch (Exception e) {
                    results.add(String.format("%s — Ошибка", name));
                }
            }
            return results;
        });

        future.thenAccept(result -> Platform.runLater(() -> {
            resultsView.setItems(FXCollections.observableArrayList(result));
            statusLabel.setText("Найдено: " + result.size() + " игроков");
            searchButton.setDisable(false);
            sortButton.setDisable(false);
            copyButton.setDisable(false); // Включаем кнопку после получения результатов
        })).exceptionally(throwable -> {
            Platform.runLater(() -> {
                statusLabel.setText("Ошибка: " + throwable.getMessage());
                searchButton.setDisable(false);
                sortButton.setDisable(false);
                copyButton.setDisable(true);
            });
            return null;
        });
    }

    private void sortResults() {
        List<String> items = new ArrayList<>(resultsView.getItems());
        items.sort((a, b) -> {
            Integer ratingA = extractRating(a);
            Integer ratingB = extractRating(b);

            if (ratingA == null && ratingB == null) return 0;
            if (ratingA == null) return 1;
            if (ratingB == null) return -1;
            return ratingB.compareTo(ratingA);
        });
        resultsView.setItems(FXCollections.observableArrayList(items));
        statusLabel.setText("Отсортировано по рейтингу (по убыванию)");
    }

    private void copyResultsToClipboard() {
        List<String> items = resultsView.getItems();
        if (items.isEmpty()) {
            statusLabel.setText("Нечего копировать");
            return;
        }

        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (String item : items) {
            // Разбираем строку вида "ФИО — Город — Рейтинг"
            String[] parts = item.split("—");
            if (parts.length < 3) continue;

            String fullName = parts[0].trim();
            String ratingStr = parts[2].trim();

            if (ratingStr.equals("—")) {
                sb.append(index).append(".").append(fullName).append(" (?)").append("\n");
            } else {
                sb.append(index).append(".").append(fullName).append(" (").append(ratingStr).append(")").append("\n");
            }
            index++;
        }

        // Убираем последний символ новой строки
        if (!sb.isEmpty()) {
            sb.setLength(sb.length() - 1);
        }

        String result = sb.toString();
        ClipboardContent content = new ClipboardContent();
        content.putString(result);
        Clipboard.getSystemClipboard().setContent(content);
        statusLabel.setText("Список скопирован в буфер обмена");
    }

    private Integer extractRating(String line) {
        String[] parts = line.split("—");
        if (parts.length < 3) return null;
        String ratingStr = parts[2].trim();
        if (ratingStr.equals("-") || ratingStr.equals("—")) return null;
        try {
            return Integer.parseInt(ratingStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> extractNames(String input) {
        return input.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .map(line -> {
                    int dotIndex = line.indexOf('.');
                    if (dotIndex > 0 && dotIndex < 10) {
                        return line.substring(dotIndex + 1).trim();
                    }
                    return line;
                })
                .collect(Collectors.toList());
    }
}