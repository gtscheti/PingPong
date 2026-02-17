package org.example.service.player.search;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class RttfPlayerSearch extends AbstractPlayerSearch {

    @Override
    protected String getBaseUrl() {
        return "https://rttf.ru/players/?type=s&name=";
    }

    @Override
    protected String getNameQueryParameter() {
        return "name";
    }

    @Override
    protected Elements getRows(Document doc) {
        return doc.select("section.players-list table tbody tr");
    }

    @Override
    protected String getName(Element row) {
        Elements cells = row.select("td");
        return cells.size() > 1 ? cells.get(1).text() : "";
    }

    @Override
    protected String getCity(Element row) {
        Elements cells = row.select("td");
        return cells.size() > 3 ? cells.get(3).text() : "";
    }

    @Override
    protected String getRatingText(Element row) {
        Elements cells = row.select("td");
        return cells.size() > 4 ? cells.get(4).text() : "";
    }

    @Override
    protected Element getLinkElement(Element row) {
        Elements cells = row.select("td");
        return cells.size() > 1 ? cells.get(1).selectFirst("a") : null;
    }
}