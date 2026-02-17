package org.example.service.player.search;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class TtwPlayerSearch extends AbstractPlayerSearch {

    @Override
    protected String getBaseUrl() {
        return "https://r.ttw.ru/players/?player-name=";
    }

    @Override
    protected String getNameQueryParameter() {
        return "player-name";
    }

    @Override
    protected Elements getRows(Document doc) {
        return doc.select("div.player-list table tbody tr");
    }

    @Override
    protected String getName(Element row) {
        return row.select("td.player-name-cell").attr("title");
    }

    @Override
    protected String getCity(Element row) {
        return row.select("td.player-city-cell").attr("title");
    }

    @Override
    protected String getRatingText(Element row) {
        return row.select("td.player-rating-cell").attr("title");
    }

    @Override
    protected Element getLinkElement(Element row) {
        return row.select("td.player-name-cell").selectFirst("a");
    }
}