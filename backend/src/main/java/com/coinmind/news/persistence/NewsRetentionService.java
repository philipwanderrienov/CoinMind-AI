package com.coinmind.news.persistence;

import com.coinmind.news.config.NewsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@ConditionalOnProperty(
        prefix = "coinmind.persistence",
        name = "enabled",
        havingValue = "true"
)
public class NewsRetentionService {

    private static final Logger log = LoggerFactory.getLogger(NewsRetentionService.class);

    private final NewsArticleRepository repository;
    private final NewsProperties properties;

    public NewsRetentionService(
            NewsArticleRepository repository,
            NewsProperties properties
    ) {
        this.repository = repository;
        this.properties = properties;
    }

    @Scheduled(cron = "${coinmind.news.retention-cron:0 15 3 * * *}")
    public void applyRetention() {
        if (!properties.retentionEnabled()) {
            return;
        }

        int hotDays = Math.max(1, properties.hotRetentionDays());
        int archiveDays = Math.max(hotDays + 1, properties.archiveRetentionDays());

        Instant now = Instant.now();
        Instant hotCutoff = now.minus(Duration.ofDays(hotDays));
        Instant archiveCutoff = now.minus(Duration.ofDays(archiveDays));

        repository.archiveOlderThan(hotCutoff)
                .flatMap(archived ->
                        repository.deleteArchiveOlderThan(archiveCutoff)
                                .doOnSuccess(deleted -> log.info(
                                        "News retention completed. archived={}, purged={}, hotDays={}, archiveDays={}",
                                        archived,
                                        deleted,
                                        hotDays,
                                        archiveDays
                                ))
                )
                .subscribe(
                        ignored -> { },
                        error -> log.warn("News retention failed", error)
                );
    }
}
