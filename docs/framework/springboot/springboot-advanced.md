# Spring Boot 고급 이론 10가지

## 📋 목차

1. [Auto Configuration 원리](#1-auto-configuration-원리)
2. [Conditional Bean 등록](#2-conditional-bean-등록)
3. [Spring Boot Starter 메커니즘](#3-spring-boot-starter-메커니즘)
4. [Application Context 계층 구조](#4-application-context-계층-구조)
5. [Profile과 Environment](#5-profile과-environment)
6. [외부화된 설정 (Externalized Configuration)](#6-외부화된-설정-externalized-configuration)
7. [Spring Boot Actuator](#7-spring-boot-actuator)
8. [내장 서버 커스터마이징](#8-내장-서버-커스터마이징)
9. [이벤트 리스너와 ApplicationListener](#9-이벤트-리스너와-applicationlistener)
10. [Bean Lifecycle과 Initialization](#10-bean-lifecycle과-initialization)

---

## 1. Auto Configuration 원리

### 1.1 정의

**Auto Configuration**은 Spring Boot가 클래스패스, 설정 파일, 빈 정의를 분석하여 자동으로 필요한 빈을 등록하는 메커니즘입니다.

### 1.2 동작 원리

```java
@SpringBootApplication
public class Application {
    // @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan
}
```

**핵심 프로세스**:
1. `@EnableAutoConfiguration`이 `META-INF/spring.factories`를 읽음
2. `AutoConfigurationImportSelector`가 조건부 빈 등록 클래스들을 스캔
3. `@ConditionalOn*` 어노테이션으로 조건 검사
4. 조건 만족 시 빈 등록

### 1.3 spring.factories 구조

```properties
# META-INF/spring.factories
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
  com.example.MyAutoConfiguration
```

### 1.4 커스텀 Auto Configuration 예제

```java
@Configuration
@ConditionalOnClass(DataSource.class)
@ConditionalOnMissingBean(DataSource.class)
@EnableConfigurationProperties(DataSourceProperties.class)
public class DataSourceAutoConfiguration {
    
    @Bean
    @ConditionalOnProperty(name = "spring.datasource.type", havingValue = "hikari")
    public DataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }
}
```

**핵심 포인트**:
- `@ConditionalOnClass`: 특정 클래스가 클래스패스에 있을 때만 활성화
- `@ConditionalOnMissingBean`: 해당 빈이 없을 때만 등록
- `@EnableConfigurationProperties`: 설정 프로퍼티 바인딩

---

## 2. Conditional Bean 등록

### 2.1 @ConditionalOn* 어노테이션 종류

| 어노테이션 | 조건 |
|-----------|------|
| `@ConditionalOnClass` | 특정 클래스가 클래스패스에 존재 |
| `@ConditionalOnMissingClass` | 특정 클래스가 클래스패스에 없음 |
| `@ConditionalOnBean` | 특정 빈이 이미 등록됨 |
| `@ConditionalOnMissingBean` | 특정 빈이 등록되지 않음 |
| `@ConditionalOnProperty` | 프로퍼티 값 조건 |
| `@ConditionalOnResource` | 리소스 파일 존재 |
| `@ConditionalOnWebApplication` | 웹 애플리케이션 컨텍스트 |
| `@ConditionalOnNotWebApplication` | 웹이 아닌 애플리케이션 |

### 2.2 실전 예제

```java
@Configuration
public class CacheConfiguration {
    
    @Bean
    @ConditionalOnProperty(name = "cache.type", havingValue = "redis")
    public CacheManager redisCacheManager() {
        return new RedisCacheManager();
    }
    
    @Bean
    @ConditionalOnProperty(name = "cache.type", havingValue = "caffeine", matchIfMissing = true)
    public CacheManager caffeineCacheManager() {
        return new CaffeineCacheManager();
    }
}
```

**핵심**: 조건부 빈 등록으로 환경별로 다른 구현체 선택 가능

---

## 3. Spring Boot Starter 메커니즘

### 3.1 Starter란?

**Starter**는 특정 기능에 필요한 의존성과 Auto Configuration을 묶어놓은 모듈입니다.

### 3.2 Starter 구조

```
spring-boot-starter-web
├── 의존성 (spring-web, spring-webmvc, tomcat-embed-core)
└── Auto Configuration (WebMvcAutoConfiguration)
```

### 3.3 커스텀 Starter 만들기

**1단계: 의존성 정의**
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-autoconfigure</artifactId>
    </dependency>
</dependencies>
```

**2단계: Auto Configuration 클래스**
```java
@Configuration
@ConditionalOnClass(MyService.class)
public class MyStarterAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public MyService myService() {
        return new MyService();
    }
}
```

**3단계: spring.factories 등록**
```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
  com.example.MyStarterAutoConfiguration
```

---

## 4. Application Context 계층 구조

### 4.1 계층 구조란?

Spring Boot는 **부모-자식 ApplicationContext** 구조를 지원합니다.

```java
// 부모 컨텍스트
AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
parent.register(ParentConfig.class);
parent.refresh();

// 자식 컨텍스트
AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
child.setParent(parent);
child.register(ChildConfig.class);
child.refresh();
```

### 4.2 빈 조회 우선순위

1. **자식 컨텍스트**에서 먼저 조회
2. 없으면 **부모 컨텍스트**에서 조회
3. 자식의 빈이 부모의 빈을 **오버라이드** 가능

### 4.3 실전 활용

**멀티 모듈 애플리케이션**:
- 공통 설정 → 부모 컨텍스트
- 모듈별 설정 → 자식 컨텍스트

---

## 5. Profile과 Environment

### 5.1 Profile 개념

**Profile**은 환경별로 다른 빈 구성을 가능하게 합니다.

```java
@Configuration
@Profile("dev")
public class DevConfig {
    @Bean
    public DataSource devDataSource() {
        return new H2DataSource();
    }
}

@Configuration
@Profile("prod")
public class ProdConfig {
    @Bean
    public DataSource prodDataSource() {
        return new HikariDataSource();
    }
}
```

### 5.2 Profile 활성화

**방법 1: application.properties**
```properties
spring.profiles.active=dev,local
```

**방법 2: 환경 변수**
```bash
export SPRING_PROFILES_ACTIVE=prod
```

**방법 3: JVM 옵션**
```bash
java -Dspring.profiles.active=prod -jar app.jar
```

### 5.3 Environment API

```java
@Autowired
private Environment env;

public void method() {
    String dbUrl = env.getProperty("spring.datasource.url");
    String[] activeProfiles = env.getActiveProfiles();
}
```

---

## 6. 외부화된 설정 (Externalized Configuration)

### 6.1 설정 파일 우선순위

Spring Boot는 다음 순서로 설정을 로드합니다:

1. **Command line arguments** (최우선)
2. **SPRING_APPLICATION_JSON** (환경 변수)
3. **ServletConfig init parameters**
4. **ServletContext init parameters**
5. **java:comp/env JNDI attributes**
6. **System.getProperties()**
7. **OS environment variables**
8. **RandomValuePropertySource**
9. **application-{profile}.properties**
10. **application.properties** (최하위)

### 6.2 @ConfigurationProperties

```java
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String name;
    private int timeout;
    private List<String> servers;
    
    // getters, setters
}
```

**application.properties**:
```properties
app.name=MyApp
app.timeout=5000
app.servers[0]=server1
app.servers[1]=server2
```

**사용**:
```java
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class Application {
    @Autowired
    private AppProperties props;
}
```

### 6.3 타입 안전한 설정 바인딩

```java
@ConfigurationProperties(prefix = "database")
@Validated
public class DatabaseProperties {
    @NotNull
    private String url;
    
    @Min(1)
    @Max(100)
    private int maxConnections;
}
```

---

## 7. Spring Boot Actuator

### 7.1 Actuator란?

**Actuator**는 프로덕션 환경에서 애플리케이션을 모니터링하고 관리할 수 있는 기능을 제공합니다.

### 7.2 주요 Endpoints

| Endpoint | 설명 |
|----------|------|
| `/actuator/health` | 애플리케이션 건강 상태 |
| `/actuator/info` | 애플리케이션 정보 |
| `/actuator/metrics` | 메트릭 정보 |
| `/actuator/env` | 환경 변수 |
| `/actuator/beans` | 등록된 빈 목록 |
| `/actuator/mappings` | 매핑 정보 |

### 7.3 Health Indicator 커스터마이징

```java
@Component
public class CustomHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        // 커스텀 체크 로직
        if (checkExternalService()) {
            return Health.up()
                .withDetail("service", "available")
                .build();
        }
        return Health.down()
            .withDetail("service", "unavailable")
            .build();
    }
}
```

### 7.4 보안 설정

```properties
# application.properties
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=when-authorized
```

---

## 8. 내장 서버 커스터마이징

### 8.1 Tomcat 커스터마이징

```java
@Configuration
public class TomcatConfig {
    
    @Bean
    public TomcatServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        factory.setPort(8080);
        factory.addConnectorCustomizers(connector -> {
            ProtocolHandler handler = connector.getProtocolHandler();
            if (handler instanceof AbstractHttp11Protocol) {
                ((AbstractHttp11Protocol<?>) handler).setMaxConnections(200);
            }
        });
        return factory;
    }
}
```

### 8.2 Undertow로 전환

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-tomcat</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-undertow</artifactId>
</dependency>
```

### 8.3 SSL 설정

```properties
server.ssl.key-store=classpath:keystore.jks
server.ssl.key-store-password=secret
server.ssl.key-store-type=JKS
server.ssl.key-alias=tomcat
```

---

## 9. 이벤트 리스너와 ApplicationListener

### 9.1 Spring Boot 이벤트 생명주기

Spring Boot는 애플리케이션 시작/종료 과정에서 여러 이벤트를 발생시킵니다:

1. `ApplicationStartingEvent` - 시작 직전
2. `ApplicationEnvironmentPreparedEvent` - Environment 준비 완료
3. `ApplicationContextInitializedEvent` - ApplicationContext 초기화
4. `ApplicationPreparedEvent` - 빈 정의 로드 완료, 빈 인스턴스화 전
5. `ApplicationStartedEvent` - 모든 빈이 준비되고 CommandLineRunner 실행 전
6. `ApplicationReadyEvent` - 애플리케이션 준비 완료
7. `ApplicationFailedEvent` - 시작 실패

### 9.2 이벤트 리스너 구현

**방법 1: @EventListener**
```java
@Component
public class MyEventListener {
    
    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        System.out.println("Context refreshed");
    }
    
    @EventListener
    @Async
    public void handleApplicationReady(ApplicationReadyEvent event) {
        System.out.println("Application is ready");
    }
}
```

**방법 2: ApplicationListener 구현**
```java
@Component
public class MyApplicationListener implements ApplicationListener<ApplicationReadyEvent> {
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // 애플리케이션 준비 완료 시 실행
    }
}
```

### 9.3 커스텀 이벤트 발행

```java
public class CustomEvent extends ApplicationEvent {
    private String message;
    
    public CustomEvent(Object source, String message) {
        super(source);
        this.message = message;
    }
}

@Component
public class EventPublisher {
    @Autowired
    private ApplicationEventPublisher publisher;
    
    public void publish(String message) {
        publisher.publishEvent(new CustomEvent(this, message));
    }
}

@Component
public class CustomEventListener {
    @EventListener
    public void handleCustomEvent(CustomEvent event) {
        System.out.println("Received: " + event.getMessage());
    }
}
```

---

## 10. Bean Lifecycle과 Initialization

### 10.1 Bean 생명주기

Spring Bean은 다음 순서로 생명주기를 가집니다:

1. **인스턴스 생성** (Constructor)
2. **의존성 주입** (@Autowired, @Value)
3. **BeanNameAware.setBeanName()**
4. **BeanFactoryAware.setBeanFactory()**
5. **ApplicationContextAware.setApplicationContext()**
6. **@PostConstruct 메서드**
7. **InitializingBean.afterPropertiesSet()**
8. **커스텀 init-method**
9. **Bean 사용**
10. **@PreDestroy 메서드**
11. **DisposableBean.destroy()**
12. **커스텀 destroy-method**

### 10.2 초기화 방법 비교

```java
@Component
public class LifecycleBean implements InitializingBean, DisposableBean {
    
    // 방법 1: @PostConstruct (권장)
    @PostConstruct
    public void init() {
        System.out.println("PostConstruct");
    }
    
    // 방법 2: InitializingBean 인터페이스
    @Override
    public void afterPropertiesSet() {
        System.out.println("afterPropertiesSet");
    }
    
    // 방법 3: @Bean의 initMethod
    // @Bean(initMethod = "customInit")
    
    // 방법 1: @PreDestroy (권장)
    @PreDestroy
    public void cleanup() {
        System.out.println("PreDestroy");
    }
    
    // 방법 2: DisposableBean 인터페이스
    @Override
    public void destroy() {
        System.out.println("destroy");
    }
}
```

### 10.3 Aware 인터페이스

```java
@Component
public class AwareBean implements BeanNameAware, ApplicationContextAware {
    
    private String beanName;
    private ApplicationContext context;
    
    @Override
    public void setBeanName(String name) {
        this.beanName = name; // 빈 이름 주입
    }
    
    @Override
    public void setApplicationContext(ApplicationContext context) {
        this.context = context; // ApplicationContext 주입
    }
}
```

### 10.4 @Lazy 초기화

```java
@Component
@Lazy
public class LazyBean {
    // 첫 사용 시점에 초기화됨
}
```

---

## 🎯 핵심 정리

### Auto Configuration
- `spring.factories`를 통한 자동 설정 등록
- `@ConditionalOn*`으로 조건부 빈 등록
- 클래스패스 기반 자동 구성

### Starter 메커니즘
- 의존성 + Auto Configuration 묶음
- 커스텀 Starter로 재사용 가능한 모듈화

### 설정 관리
- Profile로 환경별 구성 분리
- `@ConfigurationProperties`로 타입 안전한 설정 바인딩
- 설정 파일 우선순위 이해

### 생명주기 관리
- 이벤트 리스너로 시작/종료 훅 처리
- `@PostConstruct`/`@PreDestroy`로 초기화/정리
- Aware 인터페이스로 컨텍스트 정보 주입

### 운영 관리
- Actuator로 모니터링 및 관리
- 내장 서버 커스터마이징
- Health Check 커스터마이징

---

## 📚 참고

- [Spring Boot 공식 문서](https://spring.io/projects/spring-boot)
- [Auto Configuration 가이드](https://docs.spring.io/spring-boot/docs/current/reference/html/using.html#using.auto-configuration)
- [Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
