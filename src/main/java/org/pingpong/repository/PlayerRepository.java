package org.pingpong.repository;

import org.hibernate.Hibernate;
import org.pingpong.config.HibernateUtil;
import org.pingpong.model.Player;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.pingpong.model.Tournament;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PlayerRepository {

    private final org.hibernate.SessionFactory sessionFactory = HibernateUtil.getSessionFactory();

    public Player findByRttfId(String rttfId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM Player p WHERE TRIM(p.rttfId) = :rttfId", Player.class)
                    .setParameter("rttfId", rttfId.trim())
                    .setMaxResults(1)
                    .uniqueResult();
        }
    }

    public Player findByTtwId(String ttwId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM Player p WHERE TRIM(p.ttwId) = :ttwId", Player.class)
                    .setParameter("ttwId", ttwId.trim())
                    .setMaxResults(1)
                    .uniqueResult();
        }
    }

    public List<Player> findAll() {
        try (Session session = sessionFactory.openSession()) {
            List<Player> players = session.createQuery(
                            "FROM Player p LEFT JOIN FETCH p.tournamentList", Player.class)
                    .getResultList();

            // Инициализируем игры, если нужно
            for (Player player : players) {
                if (player.getTournamentList() != null) {
                    for (Tournament tournament : player.getTournamentList()) {
                        Hibernate.initialize(tournament.getGames());
                    }
                }
            }

            return players;
        }
    }

    public void save(Player player) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            if (player.getId() == null) {
                session.persist(player);
            } else {
                session.merge(player);
            }
            tx.commit();
        }
    }

    public void delete(Player player) {
        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            session.remove(session.get(Player.class, player.getId()));
            tx.commit();
        }
    }
}