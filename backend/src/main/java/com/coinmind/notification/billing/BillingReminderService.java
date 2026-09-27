package com.coinmind.notification.billing;

import com.coinmind.ai.usage.AiUsageService;
import com.coinmind.notification.config.BillingProperties;
import com.coinmind.notification.push.WebPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
public class BillingReminderService {

    private static final Logger log = LoggerFactory.getLogger(BillingReminderService.class);

    private final BillingProperties properties;
    private final WebPushService webPushService;
    private final AiUsageService usageService;
    private final ObjectProvider<BillingReminderRepository> repositoryProvider;

    public BillingReminderService(
            BillingProperties properties,
            WebPushService webPushService,
            AiUsageService usageService,
            ObjectProvider<BillingReminderRepository> repositoryProvider
    ) {
        this.properties = properties;
        this.webPushService = webPushService;
        this.usageService = usageService;
        this.repositoryProvider = repositoryProvider;
    }

    @Scheduled(
            cron = "${coinmind.billing.cron:0 0 9 * * *}",
            zone = "${coinmind.billing.timezone:Asia/Jakarta}"
    )
    public void checkBillingReminder() {
        if (!properties.enabled()) {
            return;
        }

        LocalDate today = LocalDate.now(ZoneId.of(properties.timezone()));
        LocalDate dueDate = resolveNextDueDate(today);
        long daysUntilDue = ChronoUnit.DAYS.between(today, dueDate);

        if (daysUntilDue < 0 || daysUntilDue > properties.reminderDaysBefore()) {
            return;
        }

        String reminderType = daysUntilDue == 0
                ? "DUE_TODAY"
                : "H_MINUS_" + daysUntilDue;

        BillingReminderRepository repository = repositoryProvider.getIfAvailable();

        Mono<Boolean> alreadySent = repository == null
                ? Mono.just(false)
                : repository.exists(
                        properties.provider(),
                        dueDate,
                        reminderType
                );

        alreadySent
                .filter(sent -> !sent)
                .flatMap(ignored ->
                        usageService.summary(1)
                                .flatMap(summary -> {
                                    String dueText = daysUntilDue == 0
                                            ? "jatuh tempo hari ini"
                                            : "jatuh tempo dalam " + daysUntilDue + " hari";

                                    String cost = summary.monthEstimatedCostUsd()
                                            .setScale(4, RoundingMode.HALF_UP)
                                            .toPlainString();

                                    return webPushService.sendToAll(
                                                    "CoinMind AI Billing",
                                                    "OpenAI API " + dueText
                                                            + ". Estimasi penggunaan bulan ini: $"
                                                            + cost,
                                                    "coinmind-ai-billing-" + dueDate,
                                                    "/"
                                            )
                                            .flatMap(sentCount -> {
                                                String status = sentCount > 0
                                                        ? "SENT"
                                                        : "NO_SUBSCRIBERS";

                                                log.info(
                                                        "AI billing reminder processed. dueDate={}, daysUntilDue={}, sent={}",
                                                        dueDate,
                                                        daysUntilDue,
                                                        sentCount
                                                );

                                                if (repository == null) {
                                                    return Mono.empty();
                                                }

                                                return repository.record(
                                                        properties.provider(),
                                                        dueDate,
                                                        today,
                                                        reminderType,
                                                        status
                                                );
                                            });
                                })
                )
                .subscribe(
                        ignored -> { },
                        error -> log.warn("AI billing reminder failed", error)
                );
    }

    private LocalDate resolveNextDueDate(LocalDate today) {
        int configuredDay = Math.max(1, Math.min(31, properties.dueDay()));
        YearMonth month = YearMonth.from(today);

        LocalDate candidate = month.atDay(
                Math.min(configuredDay, month.lengthOfMonth())
        );

        if (today.isAfter(candidate)) {
            YearMonth next = month.plusMonths(1);
            candidate = next.atDay(
                    Math.min(configuredDay, next.lengthOfMonth())
            );
        }

        return candidate;
    }
}
