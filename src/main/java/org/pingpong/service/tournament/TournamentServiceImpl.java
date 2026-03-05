package org.pingpong.service.tournament;

import org.pingpong.model.Tournament;
import org.pingpong.repository.TournamentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TournamentServiceImpl implements TournamentService {

    private final TournamentRepository tournamentRepository;

    public TournamentServiceImpl(TournamentRepository tournamentRepository) {
        this.tournamentRepository = tournamentRepository;
    }

    @Override
    public List<Tournament> findByPlayerId(Long playerId) {
        return tournamentRepository.findByPlayerId(playerId);
    }

    @Override
    public void update(Tournament tournament) {
        tournamentRepository.update(tournament);
    }

    @Override
    public void delete(Tournament tournament) {
        tournamentRepository.delete(tournament);
    }
}