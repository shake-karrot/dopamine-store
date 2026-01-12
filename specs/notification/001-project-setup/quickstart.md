# Quickstart: Notification 모듈 프로젝트 설정

**Feature**: notification/001-project-setup
**Date**: 2025-12-23

## Prerequisites

- JDK 17 이상
- Gradle 8.10 이상 (Wrapper 사용 권장)

### 버전 확인

```bash
java -version
# openjdk version "17.x.x" 이상

./gradlew --version
# Gradle 8.10 이상
```

## Quick Start

### 1. 프로젝트 빌드

```bash
cd notification

# 전체 빌드
./gradlew build

# 개별 모듈 빌드
./gradlew :core:build
./gradlew :adapter:build
./gradlew :app:build
./gradlew :worker:build
```

### 2. 빌드 검증

```bash
# 빌드 성공 확인 (BUILD SUCCESSFUL 메시지)
./gradlew build --info

# 모듈 목록 확인
./gradlew projects
# 출력:
# Root project 'notification'
# +--- Project ':adapter'
# +--- Project ':app'
# +--- Project ':core'
# \--- Project ':worker'
```

### 3. 의존성 확인

```bash
# core 모듈 의존성 (Spring Framework만 있어야 함)
./gradlew :core:dependencies --configuration compileClasspath

# adapter 모듈 의존성 (core 프로젝트 의존 확인)
./gradlew :adapter:dependencies --configuration compileClasspath

# app 모듈 의존성 (core, adapter 프로젝트 의존 확인)
./gradlew :app:dependencies --configuration compileClasspath

# worker 모듈 의존성 (core, adapter 프로젝트 의존 확인)
./gradlew :worker:dependencies --configuration compileClasspath
```

## Application Execution

### App 모듈 실행 (REST API)

```bash
./gradlew :app:bootRun
# 또는
cd app && ../gradlew bootRun

# 기본 포트: 8080
# Health check: http://localhost:8080/actuator/health
```

### Worker 모듈 실행 (Kafka Consumer)

```bash
./gradlew :worker:bootRun
# 또는
cd worker && ../gradlew bootRun

# 기본 포트: 8081 (app과 다른 포트)
```

## Project Structure

```
notification/
├── build.gradle.kts          # 루트 빌드 설정
├── settings.gradle.kts       # 서브모듈 설정
├── gradle.properties         # Gradle 속성
├── gradlew, gradlew.bat      # Gradle Wrapper
├── gradle/wrapper/           # Wrapper 파일
│
├── core/                     # 비즈니스 로직 (라이브러리)
│   └── build.gradle.kts
├── adapter/                  # 외부 연동 (라이브러리)
│   └── build.gradle.kts
├── app/                      # REST API (실행 가능)
│   └── build.gradle.kts
└── worker/                   # Kafka Consumer (실행 가능)
    └── build.gradle.kts
```

## Dependency Rules

| Module | Can Depend On | Cannot Depend On |
|--------|---------------|------------------|
| core | Spring Framework | adapter, app, worker |
| adapter | core | app, worker |
| app | core, adapter | worker |
| worker | core, adapter | app |

### 의존성 규칙 위반 시

```bash
# 순환 의존성 발생 시 Gradle 빌드 에러
./gradlew build
# > Circular dependency between the following tasks:
# > :core:compileKotlin
# > \--- :adapter:compileKotlin
# >      \--- :core:compileKotlin (*)
```

## Common Commands

```bash
# 클린 빌드
./gradlew clean build

# 테스트 실행
./gradlew test

# 테스트 리포트 확인
open core/build/reports/tests/test/index.html

# 의존성 트리 확인
./gradlew dependencies

# Gradle 캐시 정리
./gradlew --stop
rm -rf ~/.gradle/caches/
```

## Troubleshooting

### JDK 버전 오류

```
> Could not target platform: 'Java SE 17' using tool chain: 'JDK 11'
```

**해결**: JDK 17 이상 설치 및 JAVA_HOME 설정

```bash
export JAVA_HOME=/path/to/jdk17
```

### Gradle 버전 오류

```
> Unsupported Kotlin plugin version
```

**해결**: Gradle Wrapper 업데이트

```bash
./gradlew wrapper --gradle-version=8.10
```

### 모듈 인식 오류

```
> Project with path ':core' could not be found
```

**해결**: settings.gradle.kts에 모듈 포함 확인

```kotlin
// settings.gradle.kts
include("core", "adapter", "app", "worker")
```
