package com.dopaminestore.product.core.port

import com.dopaminestore.product.core.domain.MockProduct

interface MockPort {
    fun findById(id: String): MockProduct?
}
