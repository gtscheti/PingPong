package org.example.service.graph;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.model.Player;
import org.example.model.Tournament;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RatingChartApp extends Application {

    private static final LocalDate START_DATE = LocalDate.of(2000, 1, 1);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yy");

    public static void showRatingChart(List<Tournament> tournaments, Player player) {
        new RatingChartApp().launch(tournaments, player);
    }

    private void launch(List<Tournament> tournaments, Player player) {
        RatingChartService service = new RatingChartService();
        RatingChartData data = service.buildChartData(tournaments, player);

        Platform.runLater(() -> createAndShowStage(data, player));
    }

    private void createAndShowStage(RatingChartData data, Player player) {
        Stage stage = new Stage();
        stage.setTitle("Рейтинги: " + player.getFio());
        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/graph.png"))));
        stage.setWidth(950);
        stage.setHeight(700);

        CategoryAxis xAxis = new CategoryAxis(FXCollections.observableArrayList(
                data.dates().stream()
                        .map(date -> date.equals(START_DATE) ? "Старт" : date.format(FORMATTER))
                        .toList()
        ));
        xAxis.setLabel("Даты турниров");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Рейтинг");
        yAxis.setAutoRanging(false);
        yAxis.setTickLabelFormatter(new IntegerStringConverter());

        // Определяем начальный диапазон
        List<BigDecimal> allRatings = new ArrayList<>();
        allRatings.addAll(data.rttfRatings());
        allRatings.addAll(data.ttwRatings());

        if (!allRatings.isEmpty()) {
            BigDecimal min = allRatings.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal max = allRatings.stream().max(BigDecimal::compareTo).orElse(BigDecimal.TEN);

            int minInt = min.intValue();
            int maxInt = max.intValue();

            int padding = Math.max(10, (maxInt - minInt) / 10);
            int lowerBound = roundDown(minInt - padding);
            int upperBound = roundUp(maxInt + padding);
            int tickUnit = calculateNiceTickUnit(upperBound - lowerBound);

            yAxis.setLowerBound(lowerBound);
            yAxis.setUpperBound(upperBound);
            yAxis.setTickUnit(tickUnit);
        } else {
            yAxis.setLowerBound(0);
            yAxis.setUpperBound(100);
            yAxis.setTickUnit(10);
        }

        LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Динамика RTTF и TTW рейтингов");
        lineChart.setAnimated(false);
        lineChart.setCreateSymbols(true);

        // === Зум колесиком ===
        lineChart.setOnScroll(event -> {
            double factor = event.getDeltaY() > 0 ? 1.1 : 0.9;
            NumberAxis axis = (NumberAxis) lineChart.getYAxis();
            double oldRange = axis.getUpperBound() - axis.getLowerBound();
            double newRange = oldRange / factor;
            double centerValue = (axis.getUpperBound() + axis.getLowerBound()) / 2;

            double newLower = centerValue - newRange / 2;
            double newUpper = centerValue + newRange / 2;

            if (newRange > 10 && newLower >= 0) {
                axis.setLowerBound(newLower);
                axis.setUpperBound(newUpper);
            }
            event.consume();
        });

        // === Перетаскивание мышью (drag to pan) ===
        final double[] lastMouseY = {0};
        lineChart.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                lastMouseY[0] = event.getY();
            }
        });

        lineChart.setOnMouseDragged(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                NumberAxis axis = (NumberAxis) lineChart.getYAxis();
                double deltaY = event.getY() - lastMouseY[0];
                double scale = (axis.getUpperBound() - axis.getLowerBound()) / lineChart.getHeight(); // пикселей на единицу

                double shift = deltaY * scale;

                double newLower = axis.getLowerBound() + shift;
                double newUpper = axis.getUpperBound() + shift;

                // Ограничиваем сдвиг, чтобы не уйти в отрицательные значения
                if (newLower >= 0) {
                    axis.setLowerBound(newLower);
                    axis.setUpperBound(newUpper);
                }

                lastMouseY[0] = event.getY();
                event.consume();
            }
        });

        boolean hasData = false;

        if (!data.rttfRatings().isEmpty()) {
            addSeries(lineChart, "RTTF", "#cc0000", data.dates(), data.rttfRatings());
            hasData = true;
        }
        if (!data.ttwRatings().isEmpty()) {
            addSeries(lineChart, "TTW", "#0066cc", data.dates(), data.ttwRatings());
            hasData = true;
        }

        if (!hasData) {
            lineChart.setTitle("Нет данных о рейтингах");
        }

        VBox infoBox = createInfoPanel(data);
        StackPane root = new StackPane(lineChart, infoBox);
        StackPane.setAlignment(infoBox, Pos.TOP_LEFT);
        infoBox.setTranslateX(70);
        infoBox.setTranslateY(40);

        Scene scene = new Scene(root, 950, 700);
        stage.setScene(scene);
        stage.show();
    }

    private VBox createInfoPanel(RatingChartData data) {
        Label infoTitle = new Label("Максимумы:");
        infoTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #222; -fx-font-size: 12px;");

        Label rttfLabel = new Label(data.maxRttfDate() == null ?
                "RTTF: –" :
                String.format("RTTF: %d (%s)", data.maxRttf().intValue(),
                        data.maxRttfDate().equals(START_DATE) ? "Старт" : data.maxRttfDate().format(FORMATTER)));

        Label ttwLabel = new Label(data.maxTtwDate() == null ?
                "TTW: –" :
                String.format("TTW: %d (%s)", data.maxTtw().intValue(),
                        data.maxTtwDate().equals(START_DATE) ? "Старт" : data.maxTtwDate().format(FORMATTER)));

        VBox box = new VBox(2, infoTitle, rttfLabel, ttwLabel);
        box.setStyle("""
                -fx-background-color: rgba(255, 255, 255, 0.85);
                -fx-padding: 6; -fx-border-color: #ddd; -fx-border-radius: 3;
                -fx-background-radius: 3; -fx-font-size: 11px;
                """);
        box.setMinWidth(100);
        box.setMaxWidth(120);
        box.setPrefWidth(120);
        box.setMaxHeight(70);
        return box;
    }

    private void addSeries(LineChart<String, Number> chart, String name, String color,
                           List<LocalDate> dates, List<BigDecimal> ratings) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(name);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yy");
        ObservableList<XYChart.Data<String, Number>> dataList = FXCollections.observableArrayList();
        for (int i = 0; i < dates.size(); i++) {
            String label = dates.get(i).equals(START_DATE) ? "Старт" : dates.get(i).format(formatter);
            XYChart.Data<String, Number> data = new XYChart.Data<>(label, ratings.get(i).intValue());
            Tooltip tooltip = new Tooltip(label + "\n" + name + ": " + ratings.get(i).intValue());
            Tooltip.install(data.getNode(), tooltip);
            data.nodeProperty().addListener((obs, old, node) -> {
                if (node != null) Tooltip.install(node, tooltip);
            });
            dataList.add(data);
        }
        series.getData().setAll(dataList);
        chart.getData().add(series);

        Platform.runLater(() -> series.getNode().setStyle("-fx-stroke: %s; -fx-stroke-width: 2;".formatted(color)));
    }

    @Override
    public void start(Stage ignored) {
        // Этот метод не используется — вызов должен быть через showRatingChart()
    }

    // Вспомогательный класс для отображения целых чисел на оси Y
    private static class IntegerStringConverter extends javafx.util.StringConverter<Number> {
        @Override
        public String toString(Number object) {
            return object != null ? String.valueOf(object.intValue()) : "";
        }

        @Override
        public Number fromString(String string) {
            try {
                return string != null && !string.isEmpty() ? Integer.parseInt(string) : 0;
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    private static int roundDown(int value) {
        if (value < 0) return -roundUp(-value);
        int magnitude = (int) Math.pow(10, Math.max(0, (int) Math.log10(value) - 1));
        return (value / magnitude) * magnitude;
    }

    private static int roundUp(int value) {
        if (value < 0) return -roundDown(-value);
        int magnitude = (int) Math.pow(10, Math.max(0, (int) Math.log10(value) - 1));
        return ((value + magnitude - 1) / magnitude) * magnitude;
    }

    private static int calculateNiceTickUnit(int range) {
        double tickSpacing = range / 8.0;
        double x = Math.ceil(Math.log10(tickSpacing));
        double pow10x = Math.pow(10, x);
        double normalized = tickSpacing / pow10x;

        if (normalized <= 1) return (int) (1 * pow10x);
        if (normalized <= 2) return (int) (2 * pow10x);
        if (normalized <= 5) return (int) (5 * pow10x);
        return (int) (10 * pow10x);
    }
}