package com.dopaminestore.notification.core.domain

/**
 * 알림 유형
 */
enum class NotificationType {
    /** 회원가입 완료 */
    NEW_USER_REGISTERED,

    /** 비밀번호 재설정 요청 */
    PASSWORD_RESET_REQUESTED,

    /** 구매 슬롯 획득 */
    PURCHASE_SLOT_ACQUIRED,

    /** 구매 슬롯 만료 예정 */
    PURCHASE_SLOT_EXPIRING
}
