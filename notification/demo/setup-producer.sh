#!/bin/bash
# Producer 환경 설정 스크립트

set -e

echo "=========================================="
echo "Kafka Producer 환경 설정"
echo "=========================================="
echo

# Virtual environment 생성
if [ ! -d "venv" ]; then
    echo "1️⃣  Virtual environment 생성 중..."
    python3 -m venv venv
    echo "   ✅ Virtual environment 생성 완료"
else
    echo "1️⃣  Virtual environment가 이미 존재합니다"
fi
echo

# Virtual environment 활성화 및 의존성 설치
echo "2️⃣  의존성 설치 중..."
source venv/bin/activate
pip install -q -r requirements.txt
echo "   ✅ 의존성 설치 완료"
echo

echo "=========================================="
echo "설정 완료!"
echo "=========================================="
echo
echo "다음 명령어로 Producer를 실행하세요:"
echo
echo "  source venv/bin/activate"
echo "  python test-producer.py"
echo
