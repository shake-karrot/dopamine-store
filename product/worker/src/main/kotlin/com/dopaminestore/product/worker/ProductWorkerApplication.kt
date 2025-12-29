package com.dopaminestore.product.worker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.dopaminestore.product"])
class ProductWorkerApplication

fun main(args: Array<String>) {
    runApplication<ProductWorkerApplication>(*args)
}
