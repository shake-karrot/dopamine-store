package com.dopaminestore.product.core.service

import com.dopaminestore.product.core.domain.MockProduct
import com.dopaminestore.product.core.port.MockPort
import org.springframework.stereotype.Service

@Service
class MockService(
    private val mockPort: MockPort
) {
    fun getProduct(id: String): MockProduct? {
        return mockPort.findById(id)
    }
}
