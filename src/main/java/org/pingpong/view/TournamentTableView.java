package org.pingpong.view;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.Setter;
import org.pingpong.model.Player;
import org.pingpong.model.Tournament;
import org.pingpong.repository.TournamentRepository;
import org.pingpong.service.MainAppRefresher;
import org.pingpong.service.player.parser.TtwPlayerParser;
import org.pingpong.service.tournament.TournamentService;
import org.pingpong.service.tournament.TournamentServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Function;

public class TournamentTableView extends BorderPane {

    private final TableView<Tournament> tableView = new TableView<>();
    private final CheckBox medalsOnlyCheckBox = new CheckBox("Показать только турниры с медалями");
    private FilteredList<Tournament> filteredTournaments;
    private final Label statusLabel = new Label("Готово");

    private Player currentPlayer;
    @Setter
    private MainAppRefresher mainAppRefresher;

    TournamentRepository tournamentRepository = new TournamentRepository();
    private final TournamentService tournamentService;
    private static final Logger log = LoggerFactory.getLogger(TournamentTableView.class);

    // --- Добавим HostServices ---
    @Setter
    private HostServices hostServices;

    // --- Конструктор ---
    public TournamentTableView() {
        this.tournamentService = new TournamentServiceImpl(tournamentRepository);
        initializeUI();
        setupEventHandlers();
    }

    // --- Инициализация UI ---
    private void initializeUI() {
        setupTableColumns();
        setupTableBehavior();

        Button updatePlacesBtn = new Button("🔄 Обновить пустые места TTW");
        updatePlacesBtn.setOnAction(e -> updateEmptyTtwPlacesForCurrentPlayer());

        Button editButton = new Button();
        ImageView editIcon = new ImageView(Objects.requireNonNull(getClass().getResource("/images/edit.png")).toExternalForm());
        editIcon.setFitWidth(16);
        editIcon.setFitHeight(16);
        editButton.setGraphic(editIcon);
        editButton.setTooltip(new Tooltip("Редактировать турнир вручную"));

        editButton.setOnAction(e -> {
            Tournament selected = tableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                editTournament(selected);
            } else {
                statusLabel.setText("Выберите турнир для редактирования");
            }
        });

        statusLabel.setStyle("-fx-text-fill: blue;");

        ToolBar toolbar = new ToolBar(editButton, updatePlacesBtn, medalsOnlyCheckBox);
        setTop(toolbar);
        setCenter(tableView);
        setBottom(statusLabel);
    }

    private void setupTableColumns() {
        addColumn("Дата", "date", 70);
        addLinkColumn("Название RTTF", "rttfName", 180, this::buildRttfUrl);
        addColumn("Дельта RTTF", "rttfDelta", 80);
        addLinkColumn("Название TTW", "ttwName", 180, this::buildTtwUrl);
        addColumn("Дельта TTW", "ttwDelta", 80);
        addColumn("Место", "place", 80);

        // Настройка цвета дельт
        setDeltaCellFactory((TableColumn<Tournament, BigDecimal>) tableView.getColumns().get(2)); // rttfDelta
        setDeltaCellFactory((TableColumn<Tournament, BigDecimal>) tableView.getColumns().get(4)); // ttwDelta
    }

    private <T> void addColumn(String title, String property, double width) {
        TableColumn<Tournament, T> col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(property));
        col.setPrefWidth(width);
        tableView.getColumns().add(col);
    }

    // --- Новая колонка с гиперссылкой ---
    private void addLinkColumn(String title, String propertyName, double width, Function<Tournament, String> urlBuilder) {
        TableColumn<Tournament, String> col = new TableColumn<>(title);
        col.setPrefWidth(width);

        col.setCellFactory(tc -> new TableCell<>() {
            private final Hyperlink link = new Hyperlink();

            {
                link.setOnAction(event -> {
                    Tournament tournament = getTableView().getItems().get(getIndex());
                    String url = urlBuilder.apply(tournament);
                    if (url != null && hostServices != null) {
                        hostServices.showDocument(url);
                    } else {
                        ClipboardContent content = new ClipboardContent();
                        content.putString("Ссылка недоступна");
                        Clipboard.getSystemClipboard().setContent(content);
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    link.setText(item);
                    setGraphic(link);
                    setText(null);
                }
            }
        });

        col.setCellValueFactory(new PropertyValueFactory<>(propertyName));
        tableView.getColumns().add(col);
    }

    private String buildRttfUrl(Tournament tournament) {
        if (tournament.getRttfId() == null) return null;
        return "https://rttf.ru/tournaments/" + tournament.getRttfId();
    }

    private String buildTtwUrl(Tournament tournament) {
        if (tournament.getTtwId() == null) return null;
        return "https://r.ttw.ru/tournaments/?id=" + tournament.getTtwId();
    }

    private void setupTableBehavior() {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && tableView.getSelectionModel().getSelectedItem() != null) {
                showGamesWindow(tableView.getSelectionModel().getSelectedItem());
            }
        });
    }

    private void setupEventHandlers() {
        medalsOnlyCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> applyFilter());
    }

    public void setTournamentsForPlayer(Player player) {
        if (player == null) return;

        this.currentPlayer = player;
        statusLabel.setText("Загружаем турниры...");

        Task<List<Tournament>> task = new Task<>() {
            @Override
            protected List<Tournament> call() {
                return tournamentService.findByPlayerId(player.getId());
            }
        };

        task.setOnSucceeded(e -> {
            List<Tournament> tournaments = task.getValue();
            initializeFilteredAndSortedData(tournaments);
            updateStatusLabel();
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            Platform.runLater(() -> statusLabel.setText("❌ Ошибка: " + ex.getMessage()));
            log.error("Ошибка при загрузке турниров для игрока {} :\n {}",
                    player.getId(), ex.getMessage(), ex);
        });

        new Thread(task).start();
    }

    private void initializeFilteredAndSortedData(List<Tournament> tournaments) {
        ObservableList<Tournament> observable = FXCollections.observableArrayList(tournaments);
        filteredTournaments = new FilteredList<>(observable, t -> true);
        applyFilter();

        SortedList<Tournament> sortedTournaments = new SortedList<>(filteredTournaments);
        sortedTournaments.comparatorProperty().bind(tableView.comparatorProperty());

        tableView.setItems(sortedTournaments);
    }

    private void applyFilter() {
        if (filteredTournaments == null) return;

        Predicate<Tournament> predicate = tournament ->
                !medalsOnlyCheckBox.isSelected() || tournament.hasMedals();

        filteredTournaments.setPredicate(predicate);
        updateStatusLabel();
    }

    private void updateStatusLabel() {
        if (currentPlayer == null || filteredTournaments == null) return;

        long total = filteredTournaments.getSource().size();
        long shown = filteredTournaments.size();
        boolean filtering = medalsOnlyCheckBox.isSelected();

        String filterText = filtering ? " (с медалями)" : "";
        statusLabel.setText(String.format("✅ Турниров: %d%s → видно: %d", total, filterText, shown));
    }

    private void updateEmptyTtwPlacesForCurrentPlayer() {
        if (currentPlayer == null) {
            statusLabel.setText("❌ Нет выбранного игрока.");
            return;
        }

        Task<Void> task = new Task<>() {
            private int updatedCount = 0;
            private int errorCount = 0;

            @Override
            protected Void call() {
                List<Tournament> toUpdate = currentPlayer.getTournamentList().stream()
                        .filter(t -> t.getTtwName() != null && t.getPlace() == null)
                        .toList();

                int total = toUpdate.size();
                if (total == 0) {
                    updateMessage("Нет турниров без места для обновления.");
                    return null;
                }

                for (Tournament tournament : toUpdate) {
                    try {
                        Integer place = TtwPlayerParser.getTournamentPlace(tournament, currentPlayer.getFio());
                        if (place > 0) {
                            tournament.setPlace(place);
                            tournamentService.update(tournament);
                            updatedCount++;
                        }
                    } catch (Exception e) {
                        errorCount++;
                        log.error("Ошибка при обновлении места для турнира {} :\n {}",
                                tournament.getTtwName(), e.getMessage(), e);
                    }

                    updateProgress(updatedCount, total);
                    updateMessage("Обновлено " + updatedCount + " из " + total + ", ошибок: " + errorCount);
                }

                return null;
            }

            @Override
            protected void updateMessage(String message) {
                Platform.runLater(() -> statusLabel.setText(message));
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    statusLabel.setText("✅ Обновлено: " + updatedCount + " турниров");
                    refreshTable();
                    if (mainAppRefresher != null) mainAppRefresher.refreshPlayers();
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> statusLabel.setText("❌ Ошибка: " + getException().getMessage()));
            }
        };

        new Thread(task).start();
    }

    private void refreshTable() {
        if (currentPlayer != null) {
            setTournamentsForPlayer(currentPlayer);
        }
    }

    private void showGamesWindow(Tournament tournament) {
        Stage gamesStage = new Stage();
        gamesStage.setTitle(String.format("%s: игры — %s", currentPlayer.getFio(), formatTournamentTitle(tournament)));
        gamesStage.getIcons().add(new Image("/images/racket.png"));
        gamesStage.initModality(Modality.WINDOW_MODAL);
        gamesStage.initOwner(getScene() != null ? getScene().getWindow() : null);
        gamesStage.setMinWidth(900);
        gamesStage.setMinHeight(650);

        GamesView gamesView = new GamesView(currentPlayer);
        gamesView.loadGamesForTournament(tournament);

        Scene scene = new Scene(gamesView);
        gamesStage.setScene(scene);
        gamesStage.showAndWait();
    }

    private String formatTournamentTitle(Tournament tournament) {
        String name = tournament.getRttfName();
        if (name == null) name = tournament.getTtwName();
        return tournament.getDate() + (name != null ? (". " + name) : "");
    }

    private void setDeltaCellFactory(TableColumn<Tournament, BigDecimal> column) {
        column.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setStyle("");
                } else {
                    var delta = value.compareTo(BigDecimal.ZERO) > 0 ? "+" + value : value.toString();
                    setText(String.valueOf(delta));
                    if (value.compareTo(BigDecimal.ZERO) > 0) {
                        setStyle("-fx-text-fill: green;");
                    } else if (value.compareTo(BigDecimal.ZERO) < 0) {
                        setStyle("-fx-text-fill: red;");
                    } else {
                        setStyle(""); // ноль — без цвета
                    }
                }
            }
        });
    }

    private void editTournament(Tournament tournament) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Редактировать турнир");
        dialog.setHeaderText("Измените данные турнира:");

        TextField dateField = new TextField(tournament.getDate().toString());
        TextField placeField = new TextField(tournament.getPlace().toString());

        GridPane grid = new GridPane();
        grid.add(new Label("Дата:"), 0, 0);
        grid.add(dateField, 1, 0);
        grid.add(new Label("Место:"), 0, 1);
        grid.add(placeField, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            tournament.setDate(LocalDate.parse(dateField.getText()));
            tournament.setPlace(Integer.valueOf(placeField.getText()));
            tournamentService.update(tournament);
            tableView.refresh();
            statusLabel.setText("Турнир обновлён!");
        }
    }
}