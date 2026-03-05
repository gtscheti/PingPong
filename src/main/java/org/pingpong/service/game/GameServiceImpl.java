package org.pingpong.service.game;

import org.pingpong.model.Game;
import org.pingpong.repository.GameRepository;

import java.util.List;

public class GameServiceImpl implements GameService {

    private final GameRepository gameRepository;

    public GameServiceImpl(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    @Override
    public List<Game> findByTournamentId(Long tournamentId) {
        return gameRepository.findByTournamentId(tournamentId);
    }

    @Override
    public void delete(Game game) {
        gameRepository.delete(game);
    }
}