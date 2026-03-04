package org.pingpong.service.player;

import org.pingpong.model.Player;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public interface PlayerService {

    List<Player> findAllPlayers();
    // Новый метод для турниров игрока
    Player save(Player player, LocalDate dateFrom, Boolean fillEmptyPlaces) throws IOException;
    void deletePlayer(Player player);
}
