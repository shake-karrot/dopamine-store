package com.dopaminestore.notification.core.constant

/**
 * Kafka 토픽 상수 정의
 */
object KafkaTopics {
    /** 알림 요청 수신 토픽 */
    const val NOTIFICATION_REQUESTS = "notification.requests"

    /** Dead Letter Queue 토픽 */
    const val NOTIFICATION_REQUESTS_DLQ = "notification.requests.DLQ"

    /** Consumer Group ID */
    const val CONSUMER_GROUP_ID = "notification-consumer-group"
}
