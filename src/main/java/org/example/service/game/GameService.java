package org.example.service.game;

import org.example.model.Game;

import java.util.List;

public interface GameService {
    List<Game> findByTournamentId(Long tournamentId);
    void delete(Game game);
}