package org.pingpong.service.tournament;

import org.pingpong.model.Tournament;

import java.util.List;

public interface TournamentService {
    List<Tournament> findByPlayerId(Long playerId);
    void update(Tournament tournament);
    void delete(Tournament tournament);
}