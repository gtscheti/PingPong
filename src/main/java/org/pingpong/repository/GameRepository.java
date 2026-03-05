package org.pingpong.repository;

import org.pingpong.config.HibernateUtil;
import org.pingpong.model.Game;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class GameRepository {

    private final SessionFactory sessionFactory = HibernateUtil.getSessionFactory();

    public List<Game> findByTournamentId(Long tournamentId) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery(
                            "FROM Game g WHERE g.tournament.id = :tournamentId ORDER BY g.gameNaturalOrder, g.gameOrder", Game.class)
                    .setParameter("tournamentId", tournamentId)
                    .getResultList();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка загрузки игр турнира ID=" + tournamentId, e);
        }
    }

    public void delete(Game game) {
        if (game == null || game.getId() == null) return;

        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                Game managed = session.get(Game.class, game.getId());
                if (managed != null) {
                    session.remove(managed);
                }
                tx.commit();
            } catch (Exception e) {
                tx.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new RuntimeException("Ошибка удаления игры ID=" + game.getId(), e);
        }
    }
}