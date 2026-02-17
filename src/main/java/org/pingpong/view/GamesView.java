package org.pingpong.view;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import org.pingpong.Utils;
import org.pingpong.model.Game;
import org.pingpong.model.Player;
import org.pingpong.model.Tournament;
import org.pingpong.service.game.GameService;
import org.pingpong.service.game.GameServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

public class GamesView extends BorderPane {

    private final TableView<Game> tableView = new TableView<>();
    private final Label statusLabel = new Label("Загрузка игр...");
    private final GameService gameService = new GameServiceImpl();
    private final Player currentPlayer;
    private Tournament currentTournament;
    private static final Logger log = LoggerFactory.getLogger(GamesView.class);

    public GamesView(Player player) {
        this.currentPlayer = player;
        initializeUI();
    }

    /**
     * Инициализация UI: настройка таблицы, обработчиков и компоновка
     */
    private void initializeUI() {
        setupTable();
        setupToolbar();
        setupLayout();
    }

    private void setupTable() {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        setupGamesColumns();

        tableView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !tableView.getSelectionModel().isEmpty()) {
                showGameDetails(tableView.getSelectionModel().getSelectedItem());
            }
        });
    }

    private void setupToolbar() {
        Button refreshBtn = new Button("🔄 Обновить");
        refreshBtn.setOnAction(e -> refreshGames());

        Button deleteBtn = new Button("🗑 Удалить выбранную");
        deleteBtn.setOnAction(e -> deleteSelectedGame());
        deleteBtn.disableProperty().bind(tableView.getSelectionModel().selectedItemProperty().isNull());

        Button closeBtn = new Button("❌ Закрыть");
        closeBtn.setOnAction(e -> getScene().getWindow().hide());

        setTop(new ToolBar(refreshBtn, deleteBtn, closeBtn));
    }

    private void setupLayout() {
        setCenter(tableView);
        setBottom(statusLabel);
        setPadding(new Insets(10));
    }

    /**
     * Загрузить игры для указанного турнира
     */
    public void loadGamesForTournament(Tournament tournament) {
        this.currentTournament = tournament;
        statusLabel.setText("Загружаем игры турнира: " + tournament.getRttfName());

        Task<List<Game>> task = new Task<>() {
            @Override
            protected List<Game> call() {
                return gameService.findByTournamentId(tournament.getId());
            }
        };

        task.setOnSucceeded(e -> {
            List<Game> games = task.getValue();
            tableView.setItems(FXCollections.observableArrayList(games));
            statusLabel.setText("✅ Загружено игр: " + games.size());
        });

        task.setOnFailed(e -> {
            Throwable ex =  task.getException();
            statusLabel.setText("❌ Ошибка загрузки: " + ex.getMessage());
            log.error("Ошибка при загрузке игр для турнира {} :\n {}",
                    tournament.getId(),  ex.getMessage(), ex);
        });

        new Thread(task).start();
    }

    /**
     * Настройка колонок таблицы
     */
    private void setupGamesColumns() {
        // Соперник
        TableColumn<Game, String> opponentCol = new TableColumn<>("Соперник");
        opponentCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getOpponentName()));
        opponentCol.prefWidthProperty().bind(tableView.widthProperty().multiply(0.25));

        // Рейтинг соперника (RTTF / TTW)
        TableColumn<Game, Void> ratingHeaderCol = new TableColumn<>("Рейтинг");
        ratingHeaderCol.setPrefWidth(180);

        TableColumn<Game, Integer> rttfRatingCol = new TableColumn<>("RTTF");
        rttfRatingCol.setCellValueFactory(data -> {
            Integer rating = data.getValue().getOpponentRttfRating();
            return new SimpleIntegerProperty(rating != null ? rating : 0).asObject();
        });

        TableColumn<Game, Integer> ttwRatingCol = new TableColumn<>("TTW");
        ttwRatingCol.setCellValueFactory(data -> {
            Integer rating = data.getValue().getOpponentTtwRating();
            return new SimpleIntegerProperty(rating != null ? rating : 0).asObject();
        });

        ratingHeaderCol.getColumns().addAll(rttfRatingCol, ttwRatingCol);

        // Результат
        TableColumn<Game, String> resultCol = new TableColumn<>("Результат");
        resultCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getScore() + ":" + data.getValue().getOpponentScore()
        ));
        resultCol.prefWidthProperty().bind(tableView.widthProperty().multiply(0.12));

        // Дельта рейтинга (RTTF / TTW)
        TableColumn<Game, Void> deltaHeaderCol = new TableColumn<>("Дельта");
        deltaHeaderCol.setPrefWidth(180);

        TableColumn<Game, String> rttfDeltaCol = createDeltaColumn("RTTF", "rttfDelta");
        TableColumn<Game, String> ttwDeltaCol = createDeltaColumn("TTW", "ttwDelta");

        deltaHeaderCol.getColumns().addAll(rttfDeltaCol, ttwDeltaCol);

        tableView.getColumns().setAll(opponentCol, ratingHeaderCol, resultCol, deltaHeaderCol);
    }

    /**
     * Создание колонки дельты с цветовым выделением
     */
    private TableColumn<Game, String> createDeltaColumn(String header, String deltaType) {
        TableColumn<Game, String> column = new TableColumn<>(header);

        column.setCellValueFactory(data -> {
            BigDecimal delta = getDeltaValue(data.getValue(), deltaType);
            if (delta == null) return new SimpleStringProperty("");

            String formatted = delta.compareTo(BigDecimal.ZERO) > 0 ? "+" + delta : delta.toString();
            String colorStyle = getColorStyle(delta);

            return new SimpleStringProperty(formatted + " |" + colorStyle);
        });

        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || !item.contains("|")) {
                    setText(item);
                    setStyle("");
                    return;
                }
                String[] parts = item.split(" \\|", 2);
                setText(parts[0]);
                setStyle(parts[1]);
            }
        });

        column.prefWidthProperty().bind(tableView.widthProperty().multiply(0.15));
        return column;
    }

    private BigDecimal getDeltaValue(Game game, String deltaType) {
        return switch (deltaType) {
            case "rttfDelta" -> game.getRttfDelta();
            case "ttwDelta" -> game.getTtwDelta();
            default -> null;
        };
    }

    private String getColorStyle(BigDecimal delta) {
        return switch (delta.compareTo(BigDecimal.ZERO)) {
            case 1 -> "-fx-text-fill: #28a745;";
            case -1 -> "-fx-text-fill: #dc3545;";
            case 0 -> "-fx-text-fill: #6c757d;";
            default -> "";
        };
    }

    /**
     * Удаление выбранной игры
     */
    private void deleteSelectedGame() {
        Game selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Удалить игру");
        alert.setHeaderText("Удалить игру с " + selected.getOpponentName() + "?");
        alert.setContentText("Раунд " + selected.getGameNaturalOrder() +
                ", счет: " + selected.getScore() + ":" + selected.getOpponentScore());

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            gameService.delete(selected);
            refreshGames();
            statusLabel.setText("✅ Игра удалена");
        }
    }

    /**
     * Обновление списка игр
     */
    private void refreshGames() {
        if (currentTournament != null) {
            loadGamesForTournament(currentTournament);
        }
    }

    /**
     * Показ деталей игры
     */
    private void showGameDetails(Game game) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Детали игры");
        alert.setHeaderText(currentTournament.getDate().toString());
        alert.setContentText(String.format(
                "%s\nСчет: %s:%s\nДельта RTTF: %s, TTW: %s",
                Utils.shortenFio(currentPlayer.getFio()) + " - " + game.getOpponentName(),
                game.getScore(),
                game.getOpponentScore(),
                game.getRttfDelta() != null ? game.getRttfDelta() : "-",
                game.getTtwDelta() != null ? game.getTtwDelta() : "-"
        ));
        alert.showAndWait();
    }
}