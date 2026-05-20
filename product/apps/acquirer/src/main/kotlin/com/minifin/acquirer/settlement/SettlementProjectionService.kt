package com.minifin.acquirer.settlement

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SettlementProjectionService(
    private val repository: SettlementProjectionRepository,
) {
    @Transactional
    fun ingest(request: SettlementProjectionRequest): SettlementProjectionResponse {
        val inserted = request.items.sumOf { repository.insertProjection(it) }
        return SettlementProjectionResponse(request.items.size, inserted)
    }
}
