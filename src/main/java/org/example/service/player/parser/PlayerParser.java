package org.example.service.player.parser;

import org.example.Utils;
import org.example.model.Game;
import org.example.model.GameKey;
import org.example.model.Player;
import org.example.model.Tournament;
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
