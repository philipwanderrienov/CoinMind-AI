package com.coinmind.news.exchange;

import com.coinmind.news.config.NewsProperties;
import com.coinmind.news.service.NewsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class RssNewsCollector {

    private static final Logger log = LoggerFactory.getLogger(RssNewsCollector.class);

    private final NewsProperties properties;
    private final NewsService newsService;
    private final WebClient webClient;

    public RssNewsCollector(
            NewsProperties properties,
            NewsService newsService
    ) {
        this.properties = properties;
        this.newsService = newsService;

        HttpClient httpClient = HttpClient.create()
                .followRedirect(true);

        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(
                        HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (compatible; CoinMindAI/0.1; +https://github.com/philipwanderrienov/CoinMind-AI)"
                )
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8"
                )
                .build();
    }

    @Scheduled(fixedDelayString = "${coinmind.news.refresh-interval-ms:300000}")
    public void refresh() {
        if (!properties.enabled() || properties.rssUrls() == null) {
            return;
        }

        properties.rssUrls().stream()
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .forEach(this::fetch);
    }

    private void fetch(String url) {
        webClient.get()
                .uri(URI.create(url))
                .exchangeToMono(response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new FeedResponse(
                                        response.statusCode().value(),
                                        response.headers()
                                                .contentType()
                                                .map(MediaType::toString)
                                                .orElse("unknown"),
                                        body
                                ))
                )
                .subscribe(
                        response -> {
                            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                                log.warn(
                                        "RSS fetch returned non-success status. url={}, status={}",
                                        url,
                                        response.statusCode()
                                );
                                return;
                            }

                            if (!looksLikeXml(response.body())) {
                                log.warn(
                                        "RSS response is not XML. url={}, contentType={}, bodyPrefix={}",
                                        url,
                                        response.contentType(),
                                        bodyPrefix(response.body())
                                );
                                return;
                            }

                            parse(url, response.body());
                        },
                        error -> log.warn("RSS fetch failed. url={}", url, error)
                );
    }

    private boolean looksLikeXml(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }

        String normalized = body.stripLeading();

        return normalized.startsWith("<?xml")
                || normalized.startsWith("<rss")
                || normalized.startsWith("<feed")
                || normalized.startsWith("<rdf:RDF");
    }

    private String bodyPrefix(String body) {
        if (body == null) {
            return "";
        }

        String normalized = body
                .replaceAll("\\s+", " ")
                .trim();

        return normalized.substring(0, Math.min(normalized.length(), 160));
    }

    private void parse(String sourceUrl, String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));

            NodeList items = document.getElementsByTagName("item");

            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);

                String title = text(item, "title");
                String link = text(item, "link");
                String guid = text(item, "guid");
                String description = stripHtml(text(item, "description"));
                String pubDate = text(item, "pubDate");

                String id = !guid.isBlank()
                        ? guid
                        : (!link.isBlank() ? link : sourceUrl + "#" + title.hashCode());

                newsService.ingest(
                        id,
                        title,
                        host(sourceUrl),
                        link,
                        description,
                        parseDate(pubDate)
                );
            }
        } catch (Exception ex) {
            log.warn("RSS parse failed. url={}", sourceUrl, ex);
        }
    }

    private String text(Element element, String tag) {
        NodeList nodes = element.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    private String stripHtml(String value) {
        return value == null ? "" : value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private Instant parseDate(String value) {
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    private record FeedResponse(
            int statusCode,
            String contentType,
            String body
    ) {
    }

    private String host(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception ignored) {
            return "rss";
        }
    }
}
