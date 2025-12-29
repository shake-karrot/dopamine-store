package com.dopaminestore.product.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.dopaminestore.product"])
class ProductAppApplication

fun main(args: Array<String>) {
    runApplication<ProductAppApplication>(*args)
}
