package com.dopaminestore.product.adapter.persistence

import com.dopaminestore.product.core.domain.MockProduct
import com.dopaminestore.product.core.port.MockPort
import org.springframework.stereotype.Repository

@Repository
class MockRepositoryImpl : MockPort {
    override fun findById(id: String): MockProduct? {
        return MockProduct(id = id, name = "Mock Product")
    }
}
