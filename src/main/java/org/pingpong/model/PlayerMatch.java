package org.pingpong.model;

import lombok.Getter;

@Getter
public class PlayerMatch {
    String fullName;
    String city;
    Integer rating;
    String playerId;

    // конструктор, геттеры, toString()

    public PlayerMatch(String fullName, String city, Integer rating, String playerId) {
        this.fullName = fullName;
        this.city = city;
        this.rating = rating;
        this.playerId = playerId;
    }

    @Override
    public String toString() {
        return fullName + " (" + city + ") — рейтинг: " + rating + " | " + playerId;
    }
}
