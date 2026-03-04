package org.pingpong.service.player;

import org.pingpong.model.Player;
import org.pingpong.model.Tournament;
import org.pingpong.repository.PlayerRepository;
import org.pingpong.service.PlayerStatsService;
import org.pingpong.service.player.parser.TtwPlayerParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PlayerServiceImpl implements PlayerService {

    private final PlayerRepository playerRepository;
    private static final Logger log = LoggerFactory.getLogger(PlayerServiceImpl.class);

    public PlayerServiceImpl(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Override
    public Player save(Player player, LocalDate dateFrom, Boolean fillEmptyPlaces) throws IOException {
        // Поиск существующего игрока через репозиторий
        Player existingPlayer = findPlayerId(player);
        if (existingPlayer != null) {
            player.setId(existingPlayer.getId());
            player.setRttfRating(existingPlayer.getRttfRating());
            player.setTtwRating(existingPlayer.getTtwRating());
        }

        // Инициализация списка турниров
        if (player.getTournamentList() == null) {
            player.setTournamentList(new java.util.ArrayList<>());
        }

        // Удалить старые турниры
        player.getTournamentList().removeIf(t ->
                t.getDate() != null && !t.getDate().isBefore(dateFrom) && !t.getDate().equals(dateFrom)
        );

        // Парсинг новых турниров
        List<Tournament> newTournaments = player.ParsePlayerTournaments(dateFrom);
        for (Tournament tournament : newTournaments) {
            tournament.setPlayer(player);
            player.addTournament(tournament);
        }

        // Асинхронное заполнение мест
        var fio = player.getFio();
        List<CompletableFuture<Void>> futures = player.getTournamentList().stream()
                .filter(t -> t.getPlace() == null)
                .map(tournament -> CompletableFuture.runAsync(() -> {
                    try {
                        var ttwPlace = TtwPlayerParser.getTournamentPlace(tournament, fio);
                        tournament.setPlace(ttwPlace);
                    } catch (IOException e) {
                        log.error("Ошибка при обновлении места турнира id={}:\n {}",
                                tournament.getTtwId(), e.getMessage(), e);
                    }
                }))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Сохраняем через репозиторий
        playerRepository.save(player);
        return player;
    }

    private Player findPlayerId(Player player) {
        if (player == null) return null;
        Player existing = null;
        if (player.getRttfId() != null) {
            existing = playerRepository.findByRttfId(player.getRttfId());
        }
        if (existing == null && player.getTtwId() != null) {
            existing = playerRepository.findByTtwId(player.getTtwId());
        }
        return existing;
    }

    @Override
    public List<Player> findAllPlayers() {
        List<Player> players = playerRepository.findAll();

        PlayerStatsService statsService = new PlayerStatsService();

        for (Player player : players) {
            if (player.getTournamentList() != null) {
                player.setStats(statsService.calculate(player));
            }
        }

        return players;
    }

    @Override
    public void deletePlayer(Player player) {
        playerRepository.delete(player);
    }
}