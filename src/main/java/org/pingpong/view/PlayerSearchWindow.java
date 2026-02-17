package org.pingpong.view;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.pingpong.model.Player;
import org.pingpong.model.PlayerMatch;
import org.pingpong.service.player.PlayerService;
import org.pingpong.service.player.PlayerServiceImpl;
import org.pingpong.service.player.search.RttfPlayerSearch;
import org.pingpong.service.player.search.TtwPlayerSearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayerSearchWindow extends Stage {

    private final RttfPlayerSearch rttfSearch;
    private final TtwPlayerSearch ttwSearch;
    private final PlayerService playerService = new PlayerServiceImpl();

    private final TextField searchField = createSearchField();
    private TableView<PlayerMatch> rttfTable;
    private TableView<PlayerMatch> ttwTable;
    private final Map<String, ToggleGroup> toggleGroups = new HashMap<>();
    private Label statusLabel;
    private Button saveButton;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final AtomicInteger completedTasks = new AtomicInteger(0);
    private static final Logger log = LoggerFactory.getLogger(PlayerSearchWindow.class);

    private final Runnable onPlayerSaved;

    public PlayerSearchWindow(RttfPlayerSearch rttf, TtwPlayerSearch ttw, Runnable onPlayerSaved) {
        this.rttfSearch = rttf;
        this.ttwSearch = ttw;
        this.onPlayerSaved = onPlayerSaved;

        initializeWindow();
        setupUI();
        setupCloseOperation();
    }

    private void initializeWindow() {
        initModality(Modality.WINDOW_MODAL);
        initStyle(StageStyle.UTILITY);
        setTitle("Поиск игроков");
        setWidth(1200);
        setHeight(600);
    }

    private void setupUI() {
        statusLabel = createStatusLabel();
        saveButton = createSaveButton();

        HBox searchBox = new HBox(10, searchField, createSearchButton(), saveButton);
        searchBox.setPadding(new Insets(10));

        rttfTable = createTable("RTTF");
        ttwTable = createTable("TTW");

        HBox tablesBox = new HBox(10, rttfTable, ttwTable);
        HBox.setHgrow(rttfTable, Priority.ALWAYS);
        HBox.setHgrow(ttwTable, Priority.ALWAYS);

        ScrollPane scroll = new ScrollPane(tablesBox);
        scroll.setFitToWidth(true);

        VBox root = new VBox(10, searchBox, statusLabel, scroll);
        root.setPadding(new Insets(10));
        setScene(new Scene(root));
    }

    private Label createStatusLabel() {
        Label label = new Label("Введите ФИО игрока полностью или частично и нажмите Найти или Enter");
        label.setStyle("-fx-text-fill: blue; -fx-font-size: 12px;");
        label.setPadding(new Insets(5));
        return label;
    }

    private TextField createSearchField() {
        TextField field = new TextField();
        field.setMinWidth(250);
        field.setPromptText("Введите ФИО или часть имени...");
        field.setOnAction(event -> performSearch());
        field.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                field.clear();
            }
        });
        return field;
    }

    private Button createSearchButton() {
        Button button = new Button("Найти");
        button.setOnAction(e -> performSearch());
        return button;
    }

    private Button createSaveButton() {
        Button button = new Button("Сохранить игрока");
        button.setDisable(true);
        button.setOnAction(e -> saveSelectedPlayer());
        return button;
    }

    private TableView<PlayerMatch> createTable(String source) {
        TableView<PlayerMatch> table = new TableView<>();
        ToggleGroup group = new ToggleGroup();
        toggleGroups.put(source, group);

        TableColumn<PlayerMatch, Void> selectCol = new TableColumn<>(" ");
        selectCol.setMinWidth(40);
        selectCol.setMaxWidth(40);
        selectCol.setCellFactory(param -> new RadioButtonCell(group));
        selectCol.setSortable(false);

        TableColumn<PlayerMatch, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("playerId"));

        TableColumn<PlayerMatch, String> nameCol = new TableColumn<>("ФИО");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("fullName"));

        TableColumn<PlayerMatch, String> cityCol = new TableColumn<>("Город");
        cityCol.setCellValueFactory(new PropertyValueFactory<>("city"));

        TableColumn<PlayerMatch, Integer> ratingCol = new TableColumn<>("Рейтинг");
        ratingCol.setCellValueFactory(new PropertyValueFactory<>("rating"));

        table.getColumns().addAll(selectCol, idCol, nameCol, cityCol, ratingCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> Platform.runLater(() -> selectRadioButtonForSelectedRow(table)));

        return table;
    }

    private void performSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            statusLabel.setText("Введите имя для поиска");
            clearTables();
            return;
        }

        completedTasks.set(0);
        statusLabel.setText("Поиск игроков '" + query + "'...");
        statusLabel.setStyle("-fx-text-fill: orange;");

        executeSearchTask(rttfSearch::searchByName, "RTTF", rttfTable);
        executeSearchTask(ttwSearch::searchByName, "TTW", ttwTable);
    }

    private void executeSearchTask(SearchFunction<String, List<PlayerMatch>> searchFunc,
                                   String source, TableView<PlayerMatch> table) {
        Task<List<PlayerMatch>> task = new Task<>() {
            @Override
            protected List<PlayerMatch> call() throws Exception {
                return searchFunc.apply(searchField.getText().trim());
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            table.setItems(FXCollections.observableArrayList(task.getValue()));
            onTaskCompleted();
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            Throwable ex = task.getException();
            String msg = ex.getMessage() != null ? ex.getMessage() : "Неизвестная ошибка";
            statusLabel.setText("❌ Ошибка " + source + ": " + msg);
            statusLabel.setStyle("-fx-text-fill: red;");
            onTaskCompleted();
        }));

        executor.submit(task);
    }

    private void onTaskCompleted() {
        int completed = completedTasks.incrementAndGet();
        if (completed == 1) {
            statusLabel.setText("Выполнено 50%...");
        } else if (completed == 2) {
            Platform.runLater(() -> {
                int rttfCount = rttfTable.getItems().size();
                int ttwCount = ttwTable.getItems().size();
                statusLabel.setText(String.format("✅ Поиск завершён! Найдено: RTTF=%d, TTW=%d", rttfCount, ttwCount));
                statusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                updateSaveButton();
            });
        }
    }

    private void clearTables() {
        rttfTable.getItems().clear();
        ttwTable.getItems().clear();
    }

    public void updateSaveButton() {
        boolean hasSelection = getSelectedPlayer("RTTF") != null || getSelectedPlayer("TTW") != null;
        saveButton.setDisable(!hasSelection);
    }

    public PlayerMatch getSelectedPlayer(String source) {
        ToggleGroup group = toggleGroups.get(source);
        if (group == null) return null;
        Toggle selected = group.getSelectedToggle();
        return selected != null ? (PlayerMatch) selected.getUserData() : null;
    }

    private void saveSelectedPlayer() {
        PlayerMatch rttfPlayer = getSelectedPlayer("RTTF");
        PlayerMatch ttwPlayer = getSelectedPlayer("TTW");

        if (rttfPlayer == null && ttwPlayer == null) {
            showError("Выберите хотя бы одного игрока");
            return;
        }

        Player newPlayer = mergePlayerData(rttfPlayer, ttwPlayer);
        statusLabel.setText("Сохранение игрока " + newPlayer.getFio() + "...");

        Task<Void> saveTask = new Task<>() {
            @Override
            protected Void call() {
                playerService.save(newPlayer, LocalDate.MIN, false);
                return null;
            }
        };

        saveTask.setOnSucceeded(e -> Platform.runLater(() -> {
            statusLabel.setText("✅ Игрок " + newPlayer.getFio() + " сохранён (ID: " + newPlayer.getId() + ")");
            showSuccess(newPlayer);
            if (onPlayerSaved != null) onPlayerSaved.run();
        }));

        saveTask.setOnFailed(e -> Platform.runLater(() -> {
            Throwable ex = saveTask.getException();
            log.error("Ошибка при сохранении игрока :\n {}",
                    ex.getMessage(), ex);
            statusLabel.setText("❌ Ошибка сохранения: " + ex.getMessage());
            showError(ex.getMessage());
        }));

        executor.submit(saveTask);
    }

    private Player mergePlayerData(PlayerMatch rttf, PlayerMatch ttw) {
        Player player = new Player();
        // Приоритет RTTF, но если его нет — берём из TTW
        if (rttf != null) {
            player.setFio(rttf.getFullName());
            player.setRttfId(rttf.getPlayerId());
            player.setRttfRating(rttf.getRating());
        }
        if (ttw != null) {
            player.setFio(ttw.getFullName());
            player.setTtwId(ttw.getPlayerId());
            player.setTtwRating(ttw.getRating());
        }
        return player;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.initOwner(this);
        alert.showAndWait();
    }

    private void showSuccess(Player player) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(this);
        alert.setTitle("Добавление игрока");
        alert.setHeaderText("Игрок успешно сохранён");
        alert.setContentText(player.getFio());
        alert.showAndWait();
    }

    private void selectRadioButtonForSelectedRow(TableView<PlayerMatch> table) {
        int selectedIndex = table.getSelectionModel().getSelectedIndex();
        if (selectedIndex == -1) return;

        // Находим радиокнопку в строке
        Node radioNode = table.lookup(".table-row-cell:selected .radio-button");
        if (radioNode instanceof RadioButton radioButton) {
            radioButton.setSelected(true);
            radioButton.fire(); // активирует ToggleGroup
            updateSaveButton();
        }
    }

    private void setupCloseOperation() {
        setOnCloseRequest(e -> {
            if (!executor.isShutdown()) {
                executor.shutdown();
            }
        });
    }

    @FunctionalInterface
    private interface SearchFunction<T, R> {
        R apply(T t) throws Exception;
    }
}