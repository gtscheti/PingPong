package org.example.service.player;

import org.example.config.HibernateUtil;
import org.example.model.Player;
import org.example.model.Tournament;
import org.example.service.player.parser.TtwPlayerParser;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PlayerServiceImpl implements PlayerService {

    private final SessionFactory sessionFactory = HibernateUtil.getSessionFactory();
    private static final Logger log = LoggerFactory.getLogger(PlayerServiceImpl.class);

    @Override
    public Player save(Player player, LocalDate dateFrom, Boolean fillEmptyPlaces) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();

            // Найти существующего игрока
            Player existingPlayer = findPlayerId(player);
            if (existingPlayer == null) {
                session.persist(player);
            } else {
                player.setId(existingPlayer.getId());
                player.setRttfRating(existingPlayer.getRttfRating());
                player.setTtwRating(existingPlayer.getTtwRating());
                player = session.merge(player);
            }

            // Инициализировать список турниров
            if (player.getTournamentList() == null) {
                player.setTournamentList(new java.util.ArrayList<>());
            } else {
                Hibernate.initialize(player.getTournamentList());
            }

            // Удалить старые турниры (в той же сессии!)
            player.getTournamentList().removeIf(t ->
                    t.getDate() != null && !t.getDate().isBefore(dateFrom) && !t.getDate().equals(dateFrom)
            );

            // Парсинг и добавление новых
            List<Tournament> newTournaments = player.ParsePlayerTournaments(dateFrom);
            for (Tournament tournament : newTournaments) {
                tournament.setPlayer(player);
                player.addTournament(tournament);
            }

            // Заполнить места параллельно, если нужно
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

            // Дождаться завершения всех асинхронных задач
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            session.merge(player);
            tx.commit();
            return player;

        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw new RuntimeException("Ошибка сохранения игрока", e);
        }
    }

    private Player findPlayerId(Player player) {
        if (player == null) return null;

        try (Session session = sessionFactory.openSession()) {
            if (player.getRttfId() != null) {
                Player existing = session.createQuery(
                                "FROM Player p WHERE TRIM(p.rttfId) = :rttfId", Player.class)
                        .setParameter("rttfId", player.getRttfId().trim())
                        .setMaxResults(1)
                        .uniqueResult();
                if (existing != null) return existing;
            }
            if (player.getTtwId() != null) {
                return session.createQuery(
                                "FROM Player p WHERE TRIM(p.ttwId) = :ttwId", Player.class)
                        .setParameter("ttwId", player.getTtwId().trim())
                        .setMaxResults(1)
                        .uniqueResult();
            }
            return null;
        }
    }

    @Override
    public List<Player> findAllPlayers() {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            List<Player> players = session.createQuery("FROM Player", Player.class).getResultList();

            for (Player player : players) {
                if (player.getTournamentList() != null) {
                    Hibernate.initialize(player.getTournamentList());
                    player.refreshStats();
                }
            }

            tx.commit();
            return players;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException("Ошибка загрузки игроков", e);
        }
    }

    @Override
    public void deletePlayer(Player player) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            Player managed = session.get(Player.class, player.getId());
            if (managed != null) {
                session.remove(managed);
            }
            tx.commit();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка удаления игрока", e);
        }
    }
}