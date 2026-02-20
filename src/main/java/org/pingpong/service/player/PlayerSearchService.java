package org.pingpong.service.player;

import javafx.concurrent.Task;
import org.pingpong.model.Player;
import org.pingpong.model.PlayerMatch;
import org.pingpong.service.player.search.PlayerSearch;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayerSearchService {

    private final PlayerSearch rttfSearch;
    private final PlayerSearch ttwSearch;
    private final PlayerService playerService;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public PlayerSearchService(PlayerSearch rttfSearch, PlayerSearch ttwSearch, PlayerService playerService) {
        this.rttfSearch = rttfSearch;
        this.ttwSearch = ttwSearch;
        this.playerService = playerService;
    }

    public Task<SearchResult> searchByName(String query) {
        return new Task<>() {
            @Override
            protected SearchResult call() throws Exception {
                List<PlayerMatch> rttfResults = rttfSearch.searchByName(query);
                List<PlayerMatch> ttwResults = ttwSearch.searchByName(query);
                return new SearchResult(rttfResults, ttwResults);
            }
        };
    }

    public Task<Void> savePlayer(PlayerMatch rttf, PlayerMatch ttw) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                Player merged = mergePlayerData(rttf, ttw);
                playerService.save(merged, LocalDate.MIN, false);
                return null;
            }
        };
    }

    private Player mergePlayerData(PlayerMatch rttf, PlayerMatch ttw) {
        Player player = new Player();
        if (rttf != null) {
            player.setFio(rttf.getFullName());
            player.setRttfId(rttf.getPlayerId());
            player.setRttfRating(rttf.getRating());
        }
        if (ttw != null) {
            player.setFio(ttw.getFullName());
            player.setTtwId(ttw.getPlayerId());
            player.setTtwRating(ttw.getRating());
        }
        return player;
    }

    public void shutdown() {
        executor.shutdown();
    }

    public static class SearchResult {
        public final List<PlayerMatch> rttfResults;
        public final List<PlayerMatch> ttwResults;

        public SearchResult(List<PlayerMatch> rttfResults, List<PlayerMatch> ttwResults) {
            this.rttfResults = rttfResults;
            this.ttwResults = ttwResults;
        }
    }
}