package org.pingpong.service.tournament;

import org.pingpong.config.HibernateUtil;
import org.pingpong.model.Tournament;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.util.List;

public class TournamentServiceImpl implements TournamentService {

    private final SessionFactory sessionFactory = HibernateUtil.getSessionFactory();

    @Override
    public List<Tournament> findByPlayerId(Long playerId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM Tournament t WHERE t.player.id = :playerId ORDER BY t.date DESC", Tournament.class)
                    .setParameter("playerId", playerId)
                    .getResultList();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка загрузки турниров игрока ID=" + playerId, e);
        }
    }

    @Override
    public void update(Tournament tournament) {
        if (tournament == null || tournament.getId() == null) {
            throw new IllegalArgumentException("Турнир или его ID не может быть null");
        }

        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                session.merge(tournament);
                tx.commit();
            } catch (Exception e) {
                tx.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new RuntimeException("Ошибка обновления турнира ID=" + tournament.getId(), e);
        }
    }

    @Override
    public void delete(Tournament tournament) {
        if (tournament == null || tournament.getId() == null) return;

        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                Tournament managed = session.get(Tournament.class, tournament.getId());
                if (managed != null) {
                    session.remove(managed);
                }
                tx.commit();
            } catch (Exception e) {
                tx.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new RuntimeException("Ошибка удаления турнира ID=" + tournament.getId(), e);
        }
    }
}