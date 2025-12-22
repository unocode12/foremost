# Gradle 설정 가이드

## 목차

1. [Gradle이란?](#gradle이란)
2. [Gradle의 특징](#gradle의-특징)
3. [Gradle Wrapper의 역할](#gradle-wrapper의-역할)
4. [현재 프로젝트 Gradle 설정](#현재-프로젝트-gradle-설정)
5. [멀티 프로젝트 구조](#멀티-프로젝트-구조)
6. [주요 명령어](#주요-명령어)

---

## Gradle이란?

Gradle은 빌드 자동화 도구로, Java, Kotlin, Groovy 등 다양한 언어를 지원하는 오픈소스 빌드 시스템입니다. Apache Ant와 Apache Maven의 장점을 결합하여 개발되었으며, Groovy나 Kotlin DSL을 사용하여 빌드 스크립트를 작성할 수 있습니다.

---

## Gradle의 특징

### 1. **선언적 빌드 스크립트**

- Groovy DSL 또는 Kotlin DSL을 사용하여 간결하고 읽기 쉬운 빌드 스크립트 작성 가능
- 빌드 로직을 코드로 표현하여 유연성과 재사용성 제공

### 2. **증분 빌드 (Incremental Build)**

- 변경된 부분만 빌드하여 빌드 시간 단축
- 태스크 입력/출력을 추적하여 불필요한 재빌드 방지

### 3. **의존성 관리**

- Maven, Ivy 저장소와 호환
- 동적 의존성 버전 관리 및 충돌 해결
- 전이적 의존성(transitive dependencies) 자동 관리

### 4. **멀티 프로젝트 빌드**

- 단일 저장소에서 여러 프로젝트를 관리
- 프로젝트 간 의존성 관리 및 공통 설정 공유

### 5. **빌드 캐시**

- 로컬 및 원격 빌드 캐시 지원
- 동일한 빌드 결과를 재사용하여 빌드 성능 향상

### 6. **플러그인 생태계**

- 풍부한 플러그인 생태계
- Spring Boot, Android 등 다양한 프레임워크 지원

---

## Gradle Wrapper의 역할

Gradle Wrapper는 프로젝트에 특정 버전의 Gradle을 포함시켜, 모든 개발자가 동일한 Gradle 버전을 사용하도록 보장하는 도구입니다.

### 주요 역할

1. **버전 일관성 보장**

   - 프로젝트에 필요한 Gradle 버전을 명시적으로 지정
   - 모든 개발자가 동일한 Gradle 버전 사용

2. **설치 불필요**

   - 개발자가 별도로 Gradle을 설치할 필요 없음
   - `gradlew` 또는 `gradlew.bat` 실행 시 자동으로 필요한 Gradle 버전 다운로드

3. **CI/CD 환경 통일**
   - 빌드 서버에서도 동일한 Gradle 버전 사용
   - 환경 간 일관성 보장

### Wrapper 파일 구조

```
gradle/
└── wrapper/
    ├── gradle-wrapper.jar      # Wrapper 실행 파일
    └── gradle-wrapper.properties # Gradle 버전 및 다운로드 URL 설정
```

### gradle-wrapper.properties

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https://services.gradle.org/distributions/gradle-9.2.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- **distributionUrl**: 사용할 Gradle 버전 (현재 프로젝트: 9.2.1)
- **distributionBase/Path**: Gradle 배포판 저장 위치
- **networkTimeout**: 네트워크 타임아웃 설정

### Wrapper 사용 방법

```bash
# Linux/Mac
./gradlew <task>

# Windows
gradlew.bat <task>
```

---

## 현재 프로젝트 Gradle 설정

### 프로젝트 구조

```
foremost/
├── build.gradle              # 루트 프로젝트 빌드 설정
├── settings.gradle           # 멀티 프로젝트 설정
├── gradlew                   # Wrapper 실행 스크립트 (Unix)
├── gradlew.bat               # Wrapper 실행 스크립트 (Windows)
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── project1/
│   └── build.gradle          # project1 빌드 설정
└── project2/
    └── build.gradle          # project2 빌드 설정
```

### 1. 루트 build.gradle

```groovy
// 루트 프로젝트 설정 - 공통 설정만 포함
plugins {
	id 'java'
	id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.unocode'
version = '0.0.1-SNAPSHOT'
description = 'Multi-project Spring Boot application'

allprojects {
	repositories {
		mavenCentral()
	}
}

subprojects {
	apply plugin: 'java'
	apply plugin: 'io.spring.dependency-management'

	java {
		toolchain {
			languageVersion = JavaLanguageVersion.of(21)
		}
	}

	configurations {
		compileOnly {
			extendsFrom annotationProcessor
		}
	}

	dependencies {
		compileOnly 'org.projectlombok:lombok'
		annotationProcessor 'org.projectlombok:lombok'
		testImplementation 'org.springframework.boot:spring-boot-starter-test'
		testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
	}

	tasks.named('test') {
		useJUnitPlatform()
	}
}
```

#### 설정 설명

- **plugins**: 루트 프로젝트에 적용할 플러그인

  - `java`: Java 프로젝트 지원
  - `io.spring.dependency-management`: Spring 의존성 관리 플러그인

- **allprojects**: 모든 프로젝트(루트 포함)에 적용되는 설정

  - `repositories`: Maven Central 저장소 사용

- **subprojects**: 서브 프로젝트에만 적용되는 설정
  - Java 21 툴체인 사용
  - Lombok 의존성 및 어노테이션 프로세서 설정
  - JUnit 5 테스트 프레임워크 설정

### 2. settings.gradle

```groovy
rootProject.name = 'foremost'

include 'project1'
include 'project2'
```

- **rootProject.name**: 루트 프로젝트 이름
- **include**: 멀티 프로젝트에 포함할 서브 프로젝트 선언

### 3. project1/build.gradle

```groovy
plugins {
	id 'org.springframework.boot' version '4.0.1'
}

dependencies {
	developmentOnly 'org.springframework.boot:spring-boot-devtools'
	implementation 'org.springframework.boot:spring-boot-starter'
	implementation 'org.springframework.boot:spring-boot-starter-web'
}
```

- **org.springframework.boot**: Spring Boot 플러그인
- **spring-boot-starter**: Spring Boot 기본 스타터
- **spring-boot-starter-web**: 웹 애플리케이션 지원
- **spring-boot-devtools**: 개발 도구 (핫 리로드 등)

### 4. project2/build.gradle

project2도 project1과 동일한 구조로 설정되어 있습니다.

---

## 멀티 프로젝트 구조

현재 프로젝트는 Gradle 멀티 프로젝트 구조로 구성되어 있습니다.

### 구조의 장점

1. **코드 재사용**: 공통 설정을 루트에서 관리
2. **독립적 빌드**: 각 프로젝트를 독립적으로 빌드 및 실행 가능
3. **의존성 관리**: 프로젝트 간 의존성 명확히 정의
4. **확장성**: 새로운 프로젝트 추가가 용이

### 프로젝트 간 관계

```
foremost (루트)
├── project1 (독립 실행 가능)
└── project2 (독립 실행 가능)
```

현재는 project1과 project2가 서로 독립적이며, 공통 설정만 루트에서 상속받습니다.

### 새로운 프로젝트 추가 방법

1. `settings.gradle`에 프로젝트 추가:

   ```groovy
   include 'project3'
   ```

2. `project3` 디렉토리 및 `build.gradle` 생성:

   ```groovy
   plugins {
       id 'org.springframework.boot' version '4.0.1'
   }

   dependencies {
       implementation 'org.springframework.boot:spring-boot-starter'
   }
   ```

3. 소스 디렉토리 구조 생성:
   ```
   project3/
   ├── src/
   │   ├── main/
   │   │   ├── java/
   │   │   └── resources/
   │   └── test/
   │       └── java/
   └── build.gradle
   ```

---

## 주요 명령어

### 빌드 관련

```bash
# 전체 프로젝트 빌드
./gradlew build

# 특정 프로젝트 빌드
./gradlew :project1:build
./gradlew :project2:build

# 클린 빌드
./gradlew clean build

# 컴파일만 수행
./gradlew compileJava
./gradlew :project1:compileJava
```

### 실행 관련

```bash
# project1 실행 (포트 8081)
./gradlew :project1:bootRun

# project2 실행 (포트 8082)
./gradlew :project2:bootRun

# 백그라운드 실행 (Unix)
./gradlew :project1:bootRun &
```

### 테스트 관련

```bash
# 모든 테스트 실행
./gradlew test

# 특정 프로젝트 테스트
./gradlew :project1:test
./gradlew :project2:test

# 테스트 리포트 확인
./gradlew test --info
```

### 의존성 관련

```bash
# 의존성 트리 확인
./gradlew dependencies
./gradlew :project1:dependencies

# 의존성 업데이트 확인
./gradlew dependencyUpdates

# 의존성 다운로드
./gradlew --refresh-dependencies build
```

### 기타 유용한 명령어

```bash
# 프로젝트 구조 확인
./gradlew projects

# 태스크 목록 확인
./gradlew tasks
./gradlew :project1:tasks

# 빌드 캐시 정리
./gradlew cleanBuildCache

# Wrapper 버전 업데이트
./gradlew wrapper --gradle-version 9.2.1
```

---

## 의존성 관리

### 의존성 구성 (Configuration)

- **implementation**: 컴파일 및 런타임에 필요한 의존성
- **compileOnly**: 컴파일 시에만 필요한 의존성 (런타임 제외)
- **developmentOnly**: 개발 환경에서만 필요한 의존성
- **testImplementation**: 테스트 컴파일 및 실행에 필요한 의존성
- **testRuntimeOnly**: 테스트 실행 시에만 필요한 의존성
- **annotationProcessor**: 어노테이션 처리에 필요한 의존성

### 현재 프로젝트 주요 의존성

#### 공통 의존성 (subprojects)

- **lombok**: 보일러플레이트 코드 제거
- **spring-boot-starter-test**: 테스트 프레임워크
- **junit-platform-launcher**: JUnit 플랫폼 런처

#### 프로젝트별 의존성

- **spring-boot-starter**: Spring Boot 기본 스타터
- **spring-boot-starter-web**: 웹 애플리케이션 지원
- **spring-boot-devtools**: 개발 도구

---

## 빌드 최적화 팁

### 1. **빌드 캐시 활용**

```bash
# 빌드 캐시 활성화
./gradlew build --build-cache
```

### 2. **병렬 빌드**

```bash
# 병렬 빌드 활성화
./gradlew build --parallel
```

### 3. **데몬 프로세스 활용**

Gradle 데몬은 기본적으로 활성화되어 있어, 빌드 시작 시간을 단축합니다.

### 4. **증분 빌드**

변경된 파일만 재컴파일하므로, 전체 빌드보다 빠릅니다.

---

## 문제 해결

### 빌드 실패 시

1. **의존성 새로고침**

   ```bash
   ./gradlew clean --refresh-dependencies build
   ```

2. **빌드 캐시 정리**

   ```bash
   ./gradlew cleanBuildCache
   ```

3. **Gradle 데몬 재시작**
   ```bash
   ./gradlew --stop
   ```

### 포트 충돌 시

각 프로젝트는 다른 포트를 사용하도록 설정되어 있습니다:

- project1: 8081
- project2: 8082

포트를 변경하려면 각 프로젝트의 `application.properties` 파일을 수정하세요.

---

## 참고 자료

- [Gradle 공식 문서](https://docs.gradle.org/)
- [Spring Boot Gradle Plugin](https://docs.spring.io/spring-boot/docs/current/gradle-plugin/reference/htmlsingle/)
- [Gradle Wrapper 가이드](https://docs.gradle.org/current/userguide/gradle_wrapper.html)
