package org.pingpong.service.player.search;

import org.pingpong.model.PlayerMatch;

import java.util.List;

public interface PlayerSearch {
    List<PlayerMatch> searchByName(String rawName) throws Exception;
}
