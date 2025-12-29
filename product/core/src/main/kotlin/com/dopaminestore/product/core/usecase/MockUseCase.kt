package com.dopaminestore.product.core.usecase

import com.dopaminestore.product.core.domain.MockProduct

interface MockUseCase {
    fun execute(id: String): MockProduct?
}
