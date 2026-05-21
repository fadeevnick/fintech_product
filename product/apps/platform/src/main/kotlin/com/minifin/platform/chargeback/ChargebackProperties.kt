package com.minifin.platform.chargeback

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "minifin.chargeback")
data class ChargebackProperties(
    val disputeWindow: Duration = Duration.ofDays(60),
    val merchantResponseDeadline: Duration = Duration.ofDays(14),
)
