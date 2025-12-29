package com.dopaminestore.product.core.usecase

import com.dopaminestore.product.core.domain.MockProduct
import com.dopaminestore.product.core.service.MockService
import org.springframework.stereotype.Component

@Component
class MockUseCaseImpl(
    private val mockService: MockService
) : MockUseCase {
    override fun execute(id: String): MockProduct? {
        return mockService.getProduct(id)
    }
}
