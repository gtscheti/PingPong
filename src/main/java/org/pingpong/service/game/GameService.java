package org.pingpong.service.game;

import org.pingpong.model.Game;

import java.util.List;

public interface GameService {
    List<Game> findByTournamentId(Long tournamentId);
    void delete(Game game);
}