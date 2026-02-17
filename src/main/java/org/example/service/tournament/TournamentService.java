package org.example.service.tournament;

import org.example.model.Tournament;

import java.util.List;

public interface TournamentService {
    List<Tournament> findByPlayerId(Long playerId);
    void update(Tournament tournament);
    void delete(Tournament tournament);
}