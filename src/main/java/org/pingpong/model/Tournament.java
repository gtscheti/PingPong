package org.pingpong.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Builder
@Getter
@Setter
@With
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "TOURNAMENTS")
public class Tournament {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate date;
    private Integer place;
    private String rttfId;
    private String rttfName;
    private BigDecimal rttfDelta;
    private String ttwId;
    private String ttwName;
    private BigDecimal ttwDelta;

    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    private Player player;

    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Game> games;

    public void addGame(Game game) {
        if (games == null)
            games = new ArrayList<>();
        if (!games.contains(game)) {
            games.add(game);
            game.setTournament(this);
        }
    }

    public boolean hasMedals() {
        return getPlace() != null && (getPlace() <= 3) && (getPlace() > 0);
    }

    @Override
    public String toString() {
        return "Tournament{" +
                "date='" + date + '\'' +
                ", rttfName='" + rttfName + '\'' +
                ", ttwName='" + ttwName + '\'' +
                ", games=" + games.size() +
                '}';
    }
}
