package com.minifin.platform.merchant.webhooks

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class OutboundWebhookDispatchController(
    private val service: OutboundWebhookService,
) {
    @PostMapping("/internal/merchant/webhooks/dispatch-due")
    fun dispatchDue(@RequestParam(defaultValue = "25") limit: Int): Map<String, Int> =
        mapOf("dispatched" to service.dispatchDueRetries(limit.coerceIn(1, 100)))
}
