package org.pingpong.model;

import jakarta.persistence.*;
import lombok.*;


import java.math.BigDecimal;

@Builder
@Getter
@Setter
@With
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "GAMES")
public class Game {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer gameOrder;
    private Integer gameNaturalOrder;
    private String opponentName;
    private Integer opponentRttfRating;
    private Integer opponentTtwRating;
    private Integer score;
    private Integer opponentScore;
    private BigDecimal rttfDelta;
    private BigDecimal ttwDelta;

    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id")
    private Tournament tournament;

    @Override
    public String toString() {
        return "Game{" +
                score +
                ":" + opponentScore +
                " rttf: " + rttfDelta +
                " ttw: " + ttwDelta +
                "}";
    }

}
