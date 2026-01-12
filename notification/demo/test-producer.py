#!/usr/bin/env python3
"""
Notification Kafka Producer for Testing
이벤트를 Kafka 토픽에 발행하여 notification worker를 테스트합니다.
"""

import json
import sys
from datetime import datetime, timedelta
from uuid import uuid4

try:
    from kafka import KafkaProducer
except ImportError:
    print("Error: kafka-python 라이브러리가 필요합니다.")
    print("설치 방법: pip install kafka-python")
    sys.exit(1)


def create_producer(bootstrap_servers='localhost:9092'):
    """Kafka Producer 생성"""
    return KafkaProducer(
        bootstrap_servers=bootstrap_servers,
        value_serializer=lambda v: json.dumps(v).encode('utf-8'),
        key_serializer=lambda k: k.encode('utf-8') if k else None
    )


def send_new_user_registered(producer, email='test@example.com', user_name='홍길동'):
    """NEW_USER_REGISTERED 이벤트 발행"""
    event = {
        'eventId': f'evt-{uuid4()}',
        'eventType': 'NEW_USER_REGISTERED',
        'occurredAt': datetime.utcnow().isoformat() + 'Z',
        'userId': f'user-{uuid4()}',
        'email': email,
        'userName': user_name
    }

    future = producer.send('notification.requests', value=event, key=event['eventId'])
    result = future.get(timeout=10)

    print(f"✅ NEW_USER_REGISTERED 이벤트 발행 성공")
    print(f"   Event ID: {event['eventId']}")
    print(f"   User: {user_name} ({email})")
    print(f"   Partition: {result.partition}, Offset: {result.offset}")
    print()

    return event


def send_password_reset_requested(producer, email='forgot@example.com'):
    """PASSWORD_RESET_REQUESTED 이벤트 발행"""
    event = {
        'eventId': f'evt-{uuid4()}',
        'eventType': 'PASSWORD_RESET_REQUESTED',
        'occurredAt': datetime.utcnow().isoformat() + 'Z',
        'userId': f'user-{uuid4()}',
        'email': email,
        'resetToken': f'reset-token-{uuid4()}',
        'expiresAt': (datetime.utcnow() + timedelta(hours=1)).isoformat() + 'Z'
    }

    future = producer.send('notification.requests', value=event, key=event['eventId'])
    result = future.get(timeout=10)

    print(f"✅ PASSWORD_RESET_REQUESTED 이벤트 발행 성공")
    print(f"   Event ID: {event['eventId']}")
    print(f"   Email: {email}")
    print(f"   Partition: {result.partition}, Offset: {result.offset}")
    print()

    return event


def send_purchase_slot_acquired(producer, email='buyer@example.com', product_name='한정판 스니커즈'):
    """PURCHASE_SLOT_ACQUIRED 이벤트 발행"""
    event = {
        'eventId': f'evt-{uuid4()}',
        'eventType': 'PURCHASE_SLOT_ACQUIRED',
        'occurredAt': datetime.utcnow().isoformat() + 'Z',
        'userId': f'user-{uuid4()}',
        'email': email,
        'slotId': f'slot-{uuid4()}',
        'productId': f'prod-{uuid4()}',
        'productName': product_name,
        'expiresAt': (datetime.utcnow() + timedelta(minutes=30)).isoformat() + 'Z'
    }

    future = producer.send('notification.requests', value=event, key=event['eventId'])
    result = future.get(timeout=10)

    print(f"✅ PURCHASE_SLOT_ACQUIRED 이벤트 발행 성공")
    print(f"   Event ID: {event['eventId']}")
    print(f"   Product: {product_name}")
    print(f"   Email: {email}")
    print(f"   Partition: {result.partition}, Offset: {result.offset}")
    print()

    return event


def main():
    print("=" * 60)
    print("Notification Kafka Producer Test")
    print("=" * 60)
    print()

    # Producer 생성
    try:
        print("Kafka Producer 연결 중...")
        producer = create_producer()
        print("✅ Kafka 연결 성공 (localhost:9092)")
        print()
    except Exception as e:
        print(f"❌ Kafka 연결 실패: {e}")
        print()
        print("Docker Compose가 실행 중인지 확인하세요:")
        print("  docker-compose up -d")
        sys.exit(1)

    # 메뉴 표시
    while True:
        print("-" * 60)
        print("발행할 이벤트를 선택하세요:")
        print("  1. NEW_USER_REGISTERED (회원가입 환영)")
        print("  2. PASSWORD_RESET_REQUESTED (비밀번호 재설정)")
        print("  3. PURCHASE_SLOT_ACQUIRED (슬롯 획득)")
        print("  4. 모든 이벤트 발행 (테스트용)")
        print("  q. 종료")
        print("-" * 60)

        choice = input("선택 (1-4, q): ").strip()
        print()

        try:
            if choice == '1':
                email = input("이메일 주소 (기본: test@example.com): ").strip() or "test@example.com"
                user_name = input("사용자 이름 (기본: 홍길동): ").strip() or "홍길동"
                send_new_user_registered(producer, email, user_name)

            elif choice == '2':
                email = input("이메일 주소 (기본: forgot@example.com): ").strip() or "forgot@example.com"
                send_password_reset_requested(producer, email)

            elif choice == '3':
                email = input("이메일 주소 (기본: buyer@example.com): ").strip() or "buyer@example.com"
                product_name = input("상품명 (기본: 한정판 스니커즈): ").strip() or "한정판 스니커즈"
                send_purchase_slot_acquired(producer, email, product_name)

            elif choice == '4':
                print("모든 이벤트 타입을 발행합니다...\n")
                send_new_user_registered(producer)
                send_password_reset_requested(producer)
                send_purchase_slot_acquired(producer)
                print("✅ 모든 이벤트 발행 완료!\n")

            elif choice.lower() == 'q':
                print("종료합니다.")
                break

            else:
                print("❌ 잘못된 선택입니다.\n")

        except KeyboardInterrupt:
            print("\n\n종료합니다.")
            break
        except Exception as e:
            print(f"❌ 오류 발생: {e}\n")

    producer.close()


if __name__ == '__main__':
    main()
