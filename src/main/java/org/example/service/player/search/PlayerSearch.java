package org.example.service.player.search;

import org.example.model.PlayerMatch;

import java.util.List;

public interface PlayerSearch {
    List<PlayerMatch> searchByName(String rawName) throws Exception;
}
