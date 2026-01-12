package com.dopaminestore.notification.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.dopaminestore.notification"])
class NotificationAppApplication

fun main(args: Array<String>) {
    runApplication<NotificationAppApplication>(*args)
}
