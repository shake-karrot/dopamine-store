package com.dopaminestore.notification.worker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.dopaminestore.notification"])
class NotificationWorkerApplication

fun main(args: Array<String>) {
    runApplication<NotificationWorkerApplication>(*args)
}
