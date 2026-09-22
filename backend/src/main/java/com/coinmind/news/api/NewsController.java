package com.coinmind.news.api;

import com.coinmind.news.model.NewsArticle;
import com.coinmind.news.model.NewsSentimentSummary;
import com.coinmind.news.service.NewsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/v1/news")
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    public List<NewsArticle> recent(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return newsService.recent(limit);
    }

    @GetMapping("/{symbol}")
    public List<NewsArticle> recentForSymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return newsService.recentForSymbol(symbol, limit);
    }

    @GetMapping("/{symbol}/sentiment")
    public NewsSentimentSummary sentiment(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "6") long hours,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return newsService.summarize(
                symbol,
                Duration.ofHours(Math.max(1, Math.min(hours, 168))),
                limit
        );
    }
}
