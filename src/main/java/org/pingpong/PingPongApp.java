package org.pingpong;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.pingpong.model.Player;
import org.pingpong.service.MainAppRefresher;
import org.pingpong.service.graph.RatingChartApp;
import org.pingpong.service.player.PlayerService;
import org.pingpong.service.player.PlayerServiceImpl;
import org.pingpong.service.player.search.RttfPlayerSearch;
import org.pingpong.service.player.search.TtwPlayerSearch;
import org.pingpong.view.PlayerSearchWindow;
import org.pingpong.view.TournamentTableView;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Главное приложение для управления игроками и их турнирной статистикой.
 */
public class PingPongApp extends Application {

    private static final String ICON_PATH = "/images/racket.png";
    private static final String ADD_ICON_PATH = "/images/add.png";
    private static final String DELETE_ICON_PATH = "/images/delete.png";
    private static final String REFRESH_ICON_PATH = "/images/refresh.png";
    private static final String REFRESH_ALL_ICON_PATH = "/images/refresh_all.png";
    private static final String GOLD_MEDAL_PATH = "/images/gold.png";
    private static final String SILVER_MEDAL_PATH = "/images/silver.png";
    private static final String BRONZE_MEDAL_PATH = "/images/bronze.png";
    private static final String GRAPH_ICON_PATH = "/images/graph.png";

    private final PlayerService playerService = new PlayerServiceImpl();
    private final TableView<Player> tableView = new TableView<>();
    private final Label statusLabel = new Label();
    private final MainAppRefresher refresher = this::refreshPlayers;

    @Override
    public void start(Stage primaryStage) {
        configureStatusLabel();
        setupTableColumns();
        refreshPlayers();

        // Обработчик двойного клика — открытие турниров игрока
        tableView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Player selectedPlayer = tableView.getSelectionModel().getSelectedItem();
                if (selectedPlayer != null) {
                    showTournaments(selectedPlayer);
                }
            }
        });

        // Отображение статистики при выборе игрока
        tableView.getSelectionModel().selectedItemProperty().addListener((obs, old, current) -> {
            if (current != null) {
                statusLabel.setText(current.getStats().toString());
            }
        });

        BorderPane root = new BorderPane(tableView);
        Scene scene = new Scene(root, 1250, 600);

        primaryStage.setTitle("Игроки");
        primaryStage.getIcons().add(loadImage(ICON_PATH));
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();

        // Панель инструментов
        HBox toolbar = createToolbar();
        root.setTop(toolbar);
        root.setBottom(statusLabel);

        primaryStage.show();
    }

    /**
     * Настраивает статусную метку.
     */
    private void configureStatusLabel() {
        statusLabel.setStyle("-fx-text-fill: blue; -fx-font-size: 12px;");
        statusLabel.setPadding(new Insets(5));
    }

    /**
     * Создает панель инструментов с кнопками: добавить, удалить, обновить.
     */
    private HBox createToolbar() {
        Button addBtn = createIconButton(ADD_ICON_PATH, "Добавить игрока", e -> openPlayerSearchWindow());
        Button delBtn = createIconButton(DELETE_ICON_PATH, "Удалить игрока", e -> showDeleteConfirmationDialog());
        Button graphBtn = createIconButton(GRAPH_ICON_PATH, "График рейтингов", e -> showRatingChart());
        Button refreshBtn = createIconButton(REFRESH_ICON_PATH, "Обновить данные выбранного игрока", e -> openUpdateDateDialog((Stage) tableView.getScene().getWindow()));
        Button refreshAllBtn = createIconButton(REFRESH_ALL_ICON_PATH, "Обновить данные всех игроков", e -> refreshAllPlayers());

        HBox toolbar = new HBox(3, addBtn, delBtn, graphBtn, refreshBtn, refreshAllBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(3, 0, 3, 0));
        return toolbar;
    }

    /**
     * Создает кнопку с иконкой.
     */
    private Button createIconButton(String imagePath, String tooltipText, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        ImageView icon = new ImageView(loadImage(imagePath));
        icon.setFitWidth(20);
        icon.setFitHeight(20);
        Button button = new Button("", icon);
        button.setTooltip(new Tooltip(tooltipText));
        button.setOnAction(handler);
        return button;
    }

    /**
     * Загружает изображение из classpath.
     */
    private Image loadImage(String path) {
        return new Image(Objects.requireNonNull(getClass().getResourceAsStream(path)));
    }

    /**
     * Настраивает колонки таблицы.
     */
    private void setupTableColumns() {
        // ID и ФИО
        TableColumn<Player, Integer> idCol = addColumn("ID", "id");
        TableColumn<Player, String> fioCol = addColumn("ФИО", "fio");

        // Рейтинг
        TableColumn<Player, Void> ratingCol = new TableColumn<>("Рейтинг");
        ratingCol.getColumns().addAll(
                addColumn("RTTF", "rttfRating"),
                addColumn("TTW", "ttwRating")
        );

        // Медали
        TableColumn<Player, Void> medalsCol = new TableColumn<>("Медали");
        medalsCol.getColumns().addAll(
                createMedalColumn(GOLD_MEDAL_PATH, "firstPlaces"),
                createMedalColumn(SILVER_MEDAL_PATH, "secondPlaces"),
                createMedalColumn(BRONZE_MEDAL_PATH, "thirdPlaces")
        );

        // Турниры
        TableColumn<Player, Void> tournamentsCol = new TableColumn<>("Турниры");
        tournamentsCol.getColumns().addAll(
                addColumn("Всего", "totalTours"),
                addColumn("RTTF", "rttfTours"),
                addColumn("TTW", "ttwTours")
        );

        // Всего игр
        TableColumn<Player, Void> totalCol = new TableColumn<>("Всего");
        totalCol.getColumns().addAll(
                addColumn("Игр", "totalGames"),
                addColumn("+", "totalWins"),
                addColumn("-", "totalLosses"),
                addColumn("%", "totalWinRateFormatted")
        );

        // RTTF
        TableColumn<Player, Void> rttfCol = new TableColumn<>("RTTF");
        rttfCol.getColumns().addAll(
                addColumn("Игр", "rttfGames"),
                addColumn("+", "rttfWins"),
                addColumn("-", "rttfLosses"),
                addColumn("%", "rttfWinRateFormatted")
        );

        // TTW
        TableColumn<Player, Void> ttwCol = new TableColumn<>("TTW");
        ttwCol.getColumns().addAll(
                addColumn("Игр", "ttwGames"),
                addColumn("+", "ttwWins"),
                addColumn("-", "ttwLosses"),
                addColumn("%", "ttwWinRateFormatted")
        );

        TableColumn<Player, LocalDate> maxDateCol = addColumn("Дата", "maxDate");

        tableView.getColumns().addAll(
                idCol,
                fioCol,
                ratingCol,
                medalsCol,
                tournamentsCol,
                totalCol,
                rttfCol,
                ttwCol,
                maxDateCol
        );
    }

    /**
     * Добавляет простую колонку.
     */
    private <T> TableColumn<Player, T> addColumn(String title, String property) {
        TableColumn<Player, T> col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(property));
        return col;
    }

    /**
     * Создаёт колонку с медалью.
     */
    private TableColumn<Player, Integer> createMedalColumn(String imagePath, String property) {
        TableColumn<Player, Integer> col = new TableColumn<>();
        ImageView icon = new ImageView(loadImage(imagePath));
        icon.setFitWidth(16);
        icon.setFitHeight(16);
        col.setGraphic(icon);
        col.setCellValueFactory(new PropertyValueFactory<>(property));
        return col;
    }

    /**
     * Обновляет список игроков.
     */
    public void refreshPlayers() {
        List<Player> players = playerService.findAllPlayers();
        tableView.setItems(FXCollections.observableArrayList(players));
    }

    /**
     * Открывает окно с турнирами выбранного игрока.
     */
    private void showTournaments(Player player) {
        Stage stage = new Stage();
        stage.setTitle(player.getFio() + " - Турниры");
        stage.getIcons().add(loadImage(ICON_PATH));
        stage.initModality(Modality.WINDOW_MODAL);

        TournamentTableView tournamentView = new TournamentTableView();
        tournamentView.setMainAppRefresher(refresher);
        tournamentView.setTournamentsForPlayer(player);

        Scene scene = new Scene(new BorderPane(tournamentView), 900, 600);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Открывает окно поиска нового игрока.
     */
    private void openPlayerSearchWindow() {
        PlayerSearchWindow searchWin = new PlayerSearchWindow(
                new RttfPlayerSearch(),
                new TtwPlayerSearch(),
                this::refreshPlayers
        );
        searchWin.showAndWait();
    }

    /**
     * Диалог обновления турниров игрока с указанной даты.
     */
    private void openUpdateDateDialog(Stage owner) {
        Player selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError(owner, "Выберите игрока для обновления");
            return;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(owner);
        dialog.setTitle("Обновить турниры");
        dialog.setResizable(false);

        VBox layout = getvBox(selected, dialog);
        dialog.setScene(new Scene(layout));
        dialog.showAndWait();
    }

    private VBox getvBox(Player selected, Stage dialog) {
        DatePicker datePicker = new DatePicker(selected.getMaxDate());
        Label info = new Label("Удалить турниры после этой даты и загрузить новые:");
        info.setStyle("-fx-font-weight: bold;");

        Button ok = new Button("OK");
        ok.setOnAction(e -> {
            LocalDate date = datePicker.getValue();
            if (date != null) {
                updatePlayerWithDate(selected, date);
                dialog.close();
            }
        });

        Button cancel = new Button("Отмена");
        cancel.setOnAction(e -> dialog.close());

        HBox buttons = new HBox(10, ok, cancel);
        VBox layout = new VBox(20, info, datePicker, buttons);
        layout.setPadding(new Insets(20));
        return layout;
    }

    /**
     * Асинхронное обновление игрока с указанной даты.
     */
    private void updatePlayerWithDate(Player player, LocalDate dateFrom) {
        statusLabel.setText("🔄 Обновление турниров " + player.getFio() + "...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                playerService.save(player, dateFrom, false);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            statusLabel.setText("✅ Турниры обновлены для " + player.getFio());
            refreshPlayers();
        });

        task.setOnFailed(e -> statusLabel.setText("❌ Ошибка: " + task.getException().getMessage()));
        new Thread(task).start();
    }

    /**
     * Подтверждение удаления игрока.
     */
    private void showDeleteConfirmationDialog() {
        Player player = tableView.getSelectionModel().getSelectedItem();
        if (player == null) {
            statusLabel.setText("Выберите игрока для удаления");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Подтверждение удаления");
        alert.setHeaderText("Удалить игрока?");
        alert.setContentText(String.format("Имя: %s\nID: %d\n\nЭто действие нельзя отменить!", player.getFio(), player.getId()));

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            performDelete(player);
        } else {
            statusLabel.setText("Удаление отменено");
        }
    }

    /**
     * Асинхронное удаление игрока.
     */
    private void performDelete(Player player) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                Platform.runLater(() -> statusLabel.setText("Удаляем игрока " + player.getFio() + "..."));
                playerService.deletePlayer(player);
                return null;
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            tableView.getItems().remove(player);
            statusLabel.setText("Игрок " + player.getFio() + " удалён");
            tableView.refresh();
        }));

        task.setOnFailed(e -> Platform.runLater(() ->
                statusLabel.setText("Ошибка удаления: " + task.getException().getMessage())
        ));

        new Thread(task).start();
    }

    /**
     * Показывает диалог ошибки.
     */
    private void showError(Stage owner, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setContentText(message);
        alert.show();
    }

    /**
     * Асинхронное обновление данных для всех игроков с отображением прогресса.
     */
    private void refreshAllPlayers() {
        List<Player> players = FXCollections.observableArrayList(tableView.getItems());
        if (players.isEmpty()) {
            statusLabel.setText("Список игроков пуст.");
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                Platform.runLater(() -> statusLabel.setText("Начинаем обновление всех игроков..."));

                for (int i = 0; i < players.size(); i++) {
                    Player player = players.get(i);
                    try {
                        updateMessage(String.format("Обновление: %d/%d (%s)", i + 1, players.size(), player.getFio()));
                        int finalI = i;
                        Platform.runLater(() -> statusLabel.setText("Обновление: " + (finalI + 1) + "/" + players.size() + " — " + player.getFio()));

                        playerService.save(player, player.getMaxDate(), true); // используем последнюю дату игрока
                    } catch (Exception e) {
                        Platform.runLater(() -> statusLabel.setText("Ошибка при обновлении " + player.getFio() + ": " + e.getMessage()));
                    }
                }

                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    statusLabel.setText("✅ Все игроки обновлены.");
                    refreshPlayers();
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> statusLabel.setText("❌ Ошибка: " + getException().getMessage()));
            }
        };

        task.messageProperty().addListener((obs, old, current) -> {}); // можно использовать для прогресс-бара
        new Thread(task).start();
    }

    private void showRatingChart() {
        Player selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError((Stage) tableView.getScene().getWindow(), "Выберите игрока для отображения графика");
            return;
        }

        if (selected.getTournamentList() == null || selected.getTournamentList().isEmpty()) {
            showError((Stage) tableView.getScene().getWindow(),
                    "У игрока " + selected.getFio() + " нет турниров для построения графика");
            return;
        }

        statusLabel.setText("Строим график для " + selected.getFio() + "...");

        // Показываем график в отдельном окне
        RatingChartApp.showRatingChart(selected.getTournamentList(), selected);

        statusLabel.setText("График показан для " + selected.getFio());
    }

    public static void main(String[] args) {
        launch(args);
    }
}