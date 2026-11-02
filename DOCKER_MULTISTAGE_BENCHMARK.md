# Docker 이미지 최적화 — 멀티 스테이지 빌드 측정 결과

## 요약

Dockerfile을 **단일 스테이지 → 멀티 스테이지**로 전환하여 최종 이미지 용량을
**1.69 GB → 627 MB (약 63% 감소, 2.7배 축소)** 로 줄였다.

| 구분 | 방식 | 이미지 용량 |
|---|---|---|
| Before | 단일 스테이지 (`Dockerfile.single`) | **1.69 GB** |
| After | 멀티 스테이지 (`Dockerfile`) | **627 MB** |
| **개선** | | **-1.06 GB (63%↓)** |

> 측정 환경: Docker 27.5.1 / `eclipse-temurin:21` 베이스 / Spring Boot 3.5.6 (Java 21)

---

## 1. 문제 — 단일 스테이지 이미지에 빌드 산출물이 그대로 남음

```dockerfile
# Before: 단일 스테이지 (Dockerfile.single)
FROM eclipse-temurin:21-jdk       # JDK(개발도구 전체)가 최종 이미지에 잔존
WORKDIR /app
COPY . .                          # 소스코드 전체 포함
RUN apt-get update && apt-get install -y dos2unix
RUN dos2unix ./gradlew
RUN chmod +x ./gradlew
RUN ./gradlew build -x test --no-daemon   # Gradle 캐시·빌드 산출물 축적
RUN cp build/libs/*-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
```

최종 이미지에 **JDK 전체 + 소스코드 + Gradle 캐시 + 빌드 산출물**이 모두 포함되어
불필요하게 비대해진다. `docker history` 기준 JDK 레이어만 166 MB + 60 MB를 차지한다.

---

## 2. 해결 — 빌드 스테이지와 런타임 스테이지 분리

```dockerfile
# After: 멀티 스테이지 (Dockerfile)
# 1단계 builder — 빌드만 담당 (최종 이미지에 포함되지 않음)
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY . .
RUN apt-get update && apt-get install -y dos2unix
RUN dos2unix ./gradlew
RUN chmod +x ./gradlew
RUN ./gradlew build -x test --no-daemon

# 2단계 runtime — JRE + 실행 jar만 (builder 폐기)
FROM eclipse-temurin:21-jre        # JDK가 아닌 JRE(실행 전용, 경량)
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar   # 산출물 jar 1개만 반입
ENTRYPOINT ["java","-jar","app.jar"]
```

핵심은 `FROM ... AS builder`로 빌드 단계를 격리하고, 마지막 런타임 스테이지에서
`COPY --from=builder`로 **결과물(app.jar 80.6 MB)만** 가져와
JDK·소스·빌드 캐시를 전부 버리는 것.

| 항목 | Before (단일) | After (멀티) |
|---|---|---|
| 베이스 이미지 | `21-jdk` (개발도구 포함) | `21-jre` (실행 전용) |
| 소스코드 | 포함됨 | builder에만, 최종엔 없음 |
| Gradle 캐시/빌드산출물 | 그대로 잔존 | builder와 함께 폐기 |
| 최종 담긴 것 | 전부 | app.jar 1개 |
| **용량** | **1.69 GB** | **627 MB** |

---

## 3. 재현 방법

```bash
# Before / After 이미지 각각 빌드
docker build -t community-be-single:bench -f Dockerfile.single .
docker build -t community-be-multi:bench  -f Dockerfile .

# 용량 비교
docker images 'community-be-*'
# REPOSITORY            TAG     SIZE
# community-be-multi    bench   627MB
# community-be-single   bench   1.69GB
```

---

## 4. 결과 및 효과

- 이미지 용량 **1.69 GB → 627 MB (63% 감소)**
- 배포 시 이미지 **Pull/Push 시간 단축**, ECR 저장 용량·전송 비용 절감
- 런타임 이미지에서 JDK·빌드도구를 제거하여 **공격 표면(attack surface) 축소**

### 포트폴리오 문장 예시
> Dockerfile을 멀티 스테이지로 전환해 빌드 환경(JDK·Gradle·소스)을 런타임 이미지에서
> 분리, 이미지 용량을 **1.69GB → 627MB로 63% 절감**하여 배포 이미지 Pull/기동 시간과
> 레지스트리 저장 비용을 줄였다.
