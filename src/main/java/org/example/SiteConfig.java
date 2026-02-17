package org.example;


import org.jsoup.Connection;
import org.jsoup.Jsoup;

public enum SiteConfig {
    TTW("https://r.ttw.ru/players/?id=%s",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", 30000),
    RTTF("https://rttf.ru/results/%s",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", 10000),
    TTWTOUR("https://r.ttw.ru/tournaments/?id=%s",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", 30000);

    private final String urlTemplate;
    private final String userAgent;
    private final int timeout;

    SiteConfig(String urlTemplate, String userAgent, int timeout) {
        this.urlTemplate = urlTemplate;
        this.userAgent = userAgent;
        this.timeout = timeout;
    }

    public String buildUrl(String playerId) {
        return String.format(urlTemplate, playerId);
    }

    public Connection connect(String playerId) {
        return create(buildUrl(playerId));
    }

    public Connection create(String url) {
        return Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(timeout);
    }
}