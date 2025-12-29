package com.dopaminestore.product.app.controller

import com.dopaminestore.product.core.domain.MockProduct
import com.dopaminestore.product.core.usecase.MockUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/products")
class MockController(
    private val mockUseCase: MockUseCase
) {
    @GetMapping("/{id}")
    fun getProduct(@PathVariable id: String): MockProduct? {
        return mockUseCase.execute(id)
    }
}
