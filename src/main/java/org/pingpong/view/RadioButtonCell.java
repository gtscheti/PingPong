package org.pingpong.view;

import javafx.application.Platform;
import javafx.scene.control.*;
import org.pingpong.model.PlayerMatch;

public class RadioButtonCell extends TableCell<PlayerMatch, Void> {
    private final RadioButton radioButton;

    public RadioButtonCell(ToggleGroup group) {
        radioButton = new RadioButton();
        radioButton.setToggleGroup(group);
        radioButton.setFocusTraversable(false);
        radioButton.setPrefSize(20, 20);

        radioButton.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                Platform.runLater(() -> {
                    PlayerSearchWindow window = (PlayerSearchWindow)
                            getTableView().getScene().getWindow();
                    window.updateSaveButton();
                });
            }
        });
    }

    @Override
    protected void updateItem(Void item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || getTableRow() == null || getTableRow().getItem() == null) {
            setGraphic(null);
        } else {
            setGraphic(radioButton);
            PlayerMatch player = getTableRow().getItem();
            radioButton.setUserData(player);
        }
    }
}
