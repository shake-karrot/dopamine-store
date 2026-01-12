# Research: Notification 모듈 프로젝트 설정

**Feature**: notification/001-project-setup
**Date**: 2025-12-23

## 1. Gradle Multi-Module Project Structure

### Decision
Gradle Kotlin DSL을 사용하여 멀티모듈 프로젝트 구성

### Rationale
- Kotlin DSL은 타입 안전성과 IDE 자동완성 지원
- Spring Boot 3.x와 Kotlin 1.9.x의 공식 권장 방식
- 모듈별 독립 빌드 및 의존성 관리 용이

### Alternatives Considered
| Alternative | Rejected Because |
|-------------|------------------|
| Gradle Groovy DSL | 타입 안전성 부족, IDE 지원 약함 |
| Maven | 멀티모듈 설정 복잡, Kotlin 지원 미흡 |
| Bazel | 학습 곡선 높음, Spring Boot 통합 복잡 |

---

## 2. Gradle Version Selection

### Decision
Gradle 8.10+ 사용

### Rationale
- Kotlin 1.9.25 공식 지원
- Spring Boot 3.5.8 호환성 확인
- Configuration Cache 지원으로 빌드 성능 향상

### Alternatives Considered
| Alternative | Rejected Because |
|-------------|------------------|
| Gradle 7.x | Kotlin 1.9.x 일부 기능 미지원 |
| Gradle 8.0-8.5 | 안정성 이슈, 최신 패치 누락 |

---

## 3. Module Dependency Configuration

### Decision
build.gradle.kts에서 `implementation(project(":module"))` 형식으로 모듈 간 의존성 명시

### Rationale
- Constitution의 Module Dependencies Rules 준수
- 컴파일 타임에 순환 의존성 검출 가능
- 명시적 의존성 선언으로 아키텍처 가시성 확보

### Configuration Pattern
```kotlin
// adapter/build.gradle.kts
dependencies {
    implementation(project(":core"))
}

// app/build.gradle.kts
dependencies {
    implementation(project(":core"))
    implementation(project(":adapter"))
}

// worker/build.gradle.kts
dependencies {
    implementation(project(":core"))
    implementation(project(":adapter"))
}
```

---

## 4. Spring Boot Module Configuration

### Decision
app과 worker 모듈만 Spring Boot 애플리케이션으로 구성, core와 adapter는 라이브러리 모듈

### Rationale
- app: REST API 서버로 독립 실행 필요
- worker: Kafka Consumer로 독립 실행 필요
- core: 순수 비즈니스 로직, 실행 가능 필요 없음
- adapter: 외부 연동 구현체, 실행 가능 필요 없음

### Plugin Configuration
```kotlin
// app/build.gradle.kts
plugins {
    id("org.springframework.boot")
}

// worker/build.gradle.kts
plugins {
    id("org.springframework.boot")
}

// core/build.gradle.kts, adapter/build.gradle.kts
// Spring Boot 플러그인 없음, 라이브러리 모듈
```

---

## 5. Package Naming Convention

### Decision
`com.dopaminestore.notification.{module}` 패키지 구조 사용

### Rationale
- 도메인(dopaminestore) + 프로젝트(notification) + 모듈(core/app/worker/adapter) 계층 구조
- 다른 프로젝트(purchase, auth)와 명확한 분리
- Java/Kotlin 표준 네이밍 컨벤션 준수

### Package Structure
```
com.dopaminestore.notification.core
com.dopaminestore.notification.app
com.dopaminestore.notification.worker
com.dopaminestore.notification.adapter
```

---

## 6. Kotlin Compiler Options

### Decision
JVM 17 타겟, JSR-305 strict null-safety 적용

### Rationale
- Spring Boot 3.x는 JDK 17+ 필수
- Kotlin null-safety와 Java 라이브러리 호환성 강화
- 컴파일 타임 null 체크로 런타임 NPE 방지

### Configuration
```kotlin
kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}
```

---

## Summary

| Topic | Decision |
|-------|----------|
| Build Tool | Gradle 8.10+ with Kotlin DSL |
| Module Structure | core, app, worker, adapter (4-tier) |
| Dependency Direction | core ← adapter ← app/worker |
| Executable Modules | app, worker (Spring Boot) |
| Library Modules | core, adapter |
| Package Base | com.dopaminestore.notification |
| JVM Target | 17 |
