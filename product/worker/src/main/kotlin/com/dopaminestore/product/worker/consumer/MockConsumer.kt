package com.dopaminestore.product.worker.consumer

import com.dopaminestore.product.core.usecase.MockUseCase
import org.springframework.stereotype.Component

@Component
class MockConsumer(
    private val mockUseCase: MockUseCase
) {
    fun consume(id: String) {
        mockUseCase.execute(id)
    }
}
