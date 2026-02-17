package org.pingpong.model;


import lombok.*;

import java.util.Objects;

@Builder
@Getter
@With
@AllArgsConstructor
@NoArgsConstructor
public class GameKey {
    private Integer gameOrder;
    private String opponentName;

    @Override
    public String toString() {
        return "Opponent{" +
                "order=" + gameOrder +
                ", opponentName='" + opponentName + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameKey gameKey = (GameKey) o;
        return Objects.equals(gameOrder, gameKey.gameOrder) && Objects.equals(opponentName, gameKey.opponentName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gameOrder, opponentName);
    }
}
