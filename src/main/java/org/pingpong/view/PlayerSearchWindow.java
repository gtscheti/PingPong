package org.pingpong.view;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
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
import org.pingpong.service.player.PlayerSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayerSearchWindow extends Stage {

    private final PlayerSearchService searchService;
    private TableView<PlayerMatch> rttfTable;
    private TableView<PlayerMatch> ttwTable;
    private final Map<String, ToggleGroup> toggleGroups = new HashMap<>();
    private Label statusLabel;
    private Button saveButton;

    private final AtomicInteger completedTasks = new AtomicInteger(0);
    private static final Logger log = LoggerFactory.getLogger(PlayerSearchWindow.class);

    private final Runnable onPlayerSaved;

    public PlayerSearchWindow(PlayerSearchService searchService, Runnable onPlayerSaved) {
        this.searchService = searchService;
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

        HBox searchBox = new HBox(10, createSearchField(), createSearchButton(), saveButton);
        searchBox.setPadding(new Insets(10));

        ToggleGroup rttfGroup = new ToggleGroup();
        ToggleGroup ttwGroup = new ToggleGroup();
        toggleGroups.put("RTTF", rttfGroup);
        toggleGroups.put("TTW", ttwGroup);

        rttfTable = createTable(rttfGroup);
        ttwTable = createTable(ttwGroup);

        VBox rttfBox = wrapTableWithLabel("RTTF", rttfTable);
        VBox ttwBox = wrapTableWithLabel("TTW", ttwTable);

        HBox tablesBox = new HBox(15, rttfBox, ttwBox);
        HBox.setHgrow(rttfBox, Priority.ALWAYS);
        HBox.setHgrow(ttwBox, Priority.ALWAYS);

        ScrollPane scroll = new ScrollPane(tablesBox);
        scroll.setFitToWidth(true);

        VBox root = new VBox(10, searchBox, statusLabel, scroll);
        root.setPadding(new Insets(10));
        setScene(new Scene(root));
    }

    private VBox wrapTableWithLabel(String title, TableView<PlayerMatch> table) {
        Label label = new Label(title);
        label.setStyle("-fx-font-weight: bold;");
        label.setPadding(new Insets(0, 0, 5, 5));
        VBox box = new VBox(label, table);
        box.setSpacing(5);
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
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
        field.setOnAction(event -> performSearch(field.getText().trim()));
        field.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) field.clear();
        });
        return field;
    }

    private Button createSearchButton() {
        Button button = new Button("Найти");
        button.setOnAction(e -> performSearch(createSearchField().getText().trim()));
        return button;
    }

    private Button createSaveButton() {
        Button button = new Button("Сохранить игрока");
        button.setDisable(true);
        button.setOnAction(e -> saveSelectedPlayer());
        return button;
    }

    private TableView<PlayerMatch> createTable(ToggleGroup group) {
        TableView<PlayerMatch> table = new TableView<>();

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

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) ->
                Platform.runLater(() -> selectRadioButtonForSelectedRow(table))
        );

        return table;
    }

    private void performSearch(String query) {
        if (query.isEmpty()) {
            statusLabel.setText("Введите имя для поиска");
            clearTables();
            return;
        }

        completedTasks.set(0);
        statusLabel.setText("Поиск игроков '" + query + "'...");
        statusLabel.setStyle("-fx-text-fill: orange;");

        Task<PlayerSearchService.SearchResult> task = searchService.searchByName(query);
        task.setOnSucceeded(e -> {
            PlayerSearchService.SearchResult result = task.getValue();
            rttfTable.setItems(FXCollections.observableArrayList(result.rttfResults));
            ttwTable.setItems(FXCollections.observableArrayList(result.ttwResults));
            int rttfCount = result.rttfResults.size();
            int ttwCount = result.ttwResults.size();
            statusLabel.setText(String.format("✅ Поиск завершён! Найдено: RTTF=%d, TTW=%d", rttfCount, ttwCount));
            statusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
            updateSaveButton();
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            String msg = ex.getMessage() != null ? ex.getMessage() : "Неизвестная ошибка";
            statusLabel.setText("❌ Ошибка поиска: " + msg);
            statusLabel.setStyle("-fx-text-fill: red;");
        });

        new Thread(task).start();
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

        Task<Void> saveTask = searchService.savePlayer(rttfPlayer, ttwPlayer);
        statusLabel.setText("Сохранение игрока...");

        saveTask.setOnSucceeded(e -> Platform.runLater(() -> {
            Player saved = mergeForDisplay(rttfPlayer, ttwPlayer);
            statusLabel.setText("✅ Игрок " + saved.getFio() + " сохранён");
            showSuccess(saved);
            if (onPlayerSaved != null) onPlayerSaved.run();
        }));

        saveTask.setOnFailed(e -> Platform.runLater(() -> {
            Throwable ex = saveTask.getException();
            log.error("Ошибка при сохранении игрока", ex);
            statusLabel.setText("❌ Ошибка: " + ex.getMessage());
            showError(ex.getMessage());
        }));

        new Thread(saveTask).start();
    }

    private Player mergeForDisplay(PlayerMatch rttf, PlayerMatch ttw) {
        Player player = new Player();
        player.setFio((rttf != null ? rttf.getFullName() : ttw.getFullName()));
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

        var radioNode = table.lookup(".table-row-cell:selected .radio-button");
        if (radioNode instanceof RadioButton rb) {
            rb.setSelected(true);
            rb.fire();
            updateSaveButton();
        }
    }

    private void setupCloseOperation() {
        setOnCloseRequest(e -> {
            searchService.shutdown(); // Завершаем сервис
        });
    }
}