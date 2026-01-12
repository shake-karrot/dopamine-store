#!/bin/bash
# 로컬 테스트 환경을 빠르게 시작하는 스크립트

set -e

echo "=========================================="
echo "Notification Service 로컬 테스트 시작"
echo "=========================================="
echo

# 1. Docker Compose 실행
echo "1️⃣  Docker Compose 실행 중..."
docker-compose up -d

echo "   ⏳ Kafka와 Redis가 준비될 때까지 대기 중... (15초)"
sleep 15

echo "   ✅ 인프라 준비 완료"
echo "   - Kafka: http://localhost:9092 (KRaft 모드)"
echo "   - Redis: localhost:6379"
echo "   - Kafka UI: http://localhost:8080"
echo

# 2. 서비스 상태 확인
echo "2️⃣  서비스 상태 확인"
docker-compose ps
echo

# 3. Worker 실행 안내
echo "=========================================="
echo "다음 단계:"
echo "=========================================="
echo
echo "1. 새 터미널을 열어 Worker를 실행하세요:"
echo "   cd notification"
echo "   ./gradlew :worker:bootRun"
echo
echo "2. Worker가 실행되면 Producer로 이벤트를 발행하세요:"
echo "   cd notification/demo"
echo "   ./setup-producer.sh"
echo "   source venv/bin/activate"
echo "   python test-producer.py"
echo
echo "3. Worker 로그에서 이벤트 처리 결과를 확인하세요"
echo

read -p "Press Enter to continue..."
