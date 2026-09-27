package com.coinmind.notification.api;

import com.coinmind.notification.billing.BillingReminderService;
import com.coinmind.notification.billing.BillingStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final BillingReminderService billingReminderService;

    public BillingController(BillingReminderService billingReminderService) {
        this.billingReminderService = billingReminderService;
    }

    @GetMapping("/status")
    public BillingStatus status() {
        return billingReminderService.status();
    }
}
