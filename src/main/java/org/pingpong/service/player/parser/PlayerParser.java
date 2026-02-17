package org.pingpong.service.player.parser;

import org.pingpong.Utils;
import org.pingpong.model.Game;
import org.pingpong.model.GameKey;
import org.pingpong.model.Player;
import org.pingpong.model.Tournament;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public interface PlayerParser {

    Utils.Pair<GameKey, Element> getRatingWithResults(Player player) throws IOException;

    List<Tournament> getTournaments(Player player, Element playerResults, LocalDate dateFrom) throws IOException;

    Tournament getTournamentInfo(Element tr);

    List<Game> getGames(Element tournament);
}
