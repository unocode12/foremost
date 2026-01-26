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

#### 📋 생명주기 단계 개요

```
1. 인스턴스 생성 (Constructor)
   ↓
2. 의존성 주입 (@Autowired, @Value)
   ↓
3. BeanNameAware.setBeanName()
   ↓
4. BeanFactoryAware.setBeanFactory()
   ↓
5. ApplicationContextAware.setApplicationContext()
   ↓
6. @PostConstruct 메서드
   ↓
7. InitializingBean.afterPropertiesSet()
   ↓
8. 커스텀 init-method
   ↓
9. Bean 사용 (Ready)
   ↓
10. @PreDestroy 메서드 (종료 시)
   ↓
11. DisposableBean.destroy() (종료 시)
   ↓
12. 커스텀 destroy-method (종료 시)
```

#### 🔍 각 단계 상세 설명

##### 1️⃣ 인스턴스 생성 (Constructor)

**시점**: Bean이 처음 생성될 때

**특징**:
- 기본 생성자 또는 지정된 생성자 호출
- 이 시점에는 아직 의존성이 주입되지 않음
- `@Autowired` 필드는 `null` 상태

**예제**:
```java
@Component
public class MyBean {
    private String name;  // 아직 null
    
    public MyBean() {
        System.out.println("1. Constructor 호출");
        // name은 아직 null
    }
}
```

**주의사항**:
- 생성자에서 의존성을 사용하려고 하면 `NullPointerException` 발생
- 생성자는 가볍게 유지하고, 초기화 로직은 `@PostConstruct`에 작성

---

##### 2️⃣ 의존성 주입 (@Autowired, @Value)

**시점**: 생성자 호출 직후

**특징**:
- 필드 주입, Setter 주입, 생성자 주입 모두 이 시점에 실행
- `@Value`로 프로퍼티 값 주입
- `@Autowired` 필드에 의존성 주입

**예제**:
```java
@Component
public class MyBean {
    @Autowired
    private UserService userService;  // 이 시점에 주입됨
    
    @Value("${app.name}")
    private String appName;  // 프로퍼티 값 주입
    
    @Autowired
    public MyBean(OrderService orderService) {
        // 생성자 주입도 이 시점
        System.out.println("2. 의존성 주입 완료");
    }
}
```

**주입 순서**:
1. 생성자 주입 (생성자 파라미터)
2. 필드 주입 (`@Autowired` 필드)
3. Setter 주입 (`@Autowired` setter 메서드)

**⚠️ 순환 참조와 주입 방식**:

- **생성자 주입**: 순환 참조 발생 시 **즉시 에러** (BeanCurrentlyInCreationException)
  ```java
  // ❌ 순환 참조 에러 발생
  @Service
  public class A {
      public A(B b) {}  // B 필요
  }
  
  @Service
  public class B {
      public B(A a) {}  // A 필요 → 순환 참조!
  }
  ```

- **필드 주입**: Spring이 **3단계 캐싱**으로 순환 참조 해결 시도
  ```java
  // ⚠️ 순환 참조 허용 (하지만 권장하지 않음)
  @Service
  public class A {
      @Autowired
      private B b;  // 프록시로 먼저 주입
  }
  
  @Service
  public class B {
      @Autowired
      private A a;  // 프록시로 먼저 주입
  }
  ```

- **해결 방법**: `@Lazy` 사용 또는 설계 개선
  ```java
  // ✅ @Lazy로 순환 참조 해결
  @Service
  public class A {
      @Autowired
      @Lazy
      private B b;  // 지연 초기화
  }
  ```

---

##### 3️⃣ BeanNameAware.setBeanName()

**시점**: 의존성 주입 직후

**특징**:
- Bean의 이름을 주입받을 수 있음
- `BeanNameAware` 인터페이스 구현 필요
- Bean 이름은 기본적으로 클래스명의 첫 글자를 소문자로 변환

**예제**:
```java
@Component
public class MyBean implements BeanNameAware {
    private String beanName;
    
    @Override
    public void setBeanName(String name) {
        this.beanName = name;
        System.out.println("3. Bean 이름: " + name);  // "myBean"
    }
}
```

**사용 사례**:
- 로깅 시 Bean 이름 포함
- 동적 Bean 선택
- 디버깅 목적

---

##### 4️⃣ BeanFactoryAware.setBeanFactory()

**시점**: BeanNameAware 이후

**특징**:
- `BeanFactory`를 주입받을 수 있음
- `BeanFactoryAware` 인터페이스 구현 필요
- Bean 생성/조회 등 BeanFactory 기능 사용 가능

**예제**:
```java
@Component
public class MyBean implements BeanFactoryAware {
    private BeanFactory beanFactory;
    
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
        System.out.println("4. BeanFactory 주입 완료");
    }
    
    public void useBeanFactory() {
        // 다른 Bean 조회
        UserService userService = beanFactory.getBean(UserService.class);
    }
}
```

**사용 사례**:
- 동적 Bean 조회
- 프로토타입 Bean 생성
- Bean 존재 여부 확인

---

##### 5️⃣ ApplicationContextAware.setApplicationContext()

**시점**: BeanFactoryAware 이후

**특징**:
- `ApplicationContext`를 주입받을 수 있음
- `ApplicationContextAware` 인터페이스 구현 필요
- BeanFactory보다 더 많은 기능 제공 (이벤트 발행, 메시지 소스 등)

**예제**:
```java
@Component
public class MyBean implements ApplicationContextAware {
    private ApplicationContext applicationContext;
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        System.out.println("5. ApplicationContext 주입 완료");
    }
    
    public void publishEvent() {
        // 이벤트 발행
        applicationContext.publishEvent(new MyEvent("Hello"));
    }
}
```

**사용 사례**:
- 이벤트 발행/구독
- 메시지 소스 사용
- 프로파일 확인
- Bean 조회

**⚠️ 주의**: ApplicationContext를 필드로 저장하는 것은 안티패턴일 수 있음. 가능하면 생성자 주입 사용

---

##### 6️⃣ @PostConstruct 메서드

**시점**: 모든 Aware 인터페이스 호출 후

**특징**:
- **가장 권장되는 초기화 방법**
- JSR-250 표준 어노테이션
- 의존성 주입이 완료된 후 실행
- 여러 개의 `@PostConstruct` 메서드가 있으면 순서 보장 안 됨

**예제**:
```java
@Component
public class MyBean {
    @Autowired
    private UserService userService;
    
    @PostConstruct
    public void init() {
        System.out.println("6. @PostConstruct 호출");
        // 모든 의존성이 주입된 상태
        // 초기화 로직 작성
        userService.initialize();
    }
}
```

**사용 사례**:
- 데이터베이스 연결 확인
- 캐시 초기화
- 외부 서비스 연결 확인
- 초기 데이터 로딩

**장점**:
- 표준 어노테이션 (JSR-250)
- 인터페이스 구현 불필요
- 코드 간결

---

##### 7️⃣ InitializingBean.afterPropertiesSet()

**시점**: @PostConstruct 이후

**특징**:
- `InitializingBean` 인터페이스 구현 필요
- Spring 전용 인터페이스
- `@PostConstruct`와 동일한 목적이지만 Spring에 종속적

**예제**:
```java
@Component
public class MyBean implements InitializingBean {
    @Override
    public void afterPropertiesSet() throws Exception {
        System.out.println("7. afterPropertiesSet 호출");
        // 초기화 로직
    }
}
```

**⚠️ 권장사항**: `@PostConstruct` 사용 권장 (표준이므로)

**사용 사례**:
- 레거시 코드 호환
- Spring 전용 초기화 로직

---

##### 8️⃣ 커스텀 init-method

**시점**: afterPropertiesSet() 이후

**특징**:
- `@Bean(initMethod = "methodName")` 또는 XML 설정
- 임의의 메서드명 사용 가능
- 반환 타입은 `void`, 파라미터 없음

**예제**:
```java
@Configuration
public class AppConfig {
    @Bean(initMethod = "customInit")
    public MyBean myBean() {
        return new MyBean();
    }
}

public class MyBean {
    public void customInit() {
        System.out.println("8. 커스텀 init-method 호출");
    }
}
```

**사용 사례**:
- 외부 라이브러리 클래스 초기화
- XML 설정에서 Java 설정으로 마이그레이션
- 특정 초기화 메서드명이 필요한 경우

---

##### 9️⃣ Bean 사용 (Ready)

**시점**: 모든 초기화 완료 후

**특징**:
- Bean이 완전히 준비된 상태
- 다른 Bean에서 주입받아 사용 가능
- 비즈니스 로직 실행

**예제**:
```java
@Service
public class OrderService {
    @Autowired
    private MyBean myBean;  // 완전히 초기화된 Bean 사용
    
    public void processOrder() {
        myBean.doSomething();  // 안전하게 사용 가능
    }
}
```

---

##### 🔟 @PreDestroy 메서드

**시점**: ApplicationContext 종료 시 (종료 단계)

**특징**:
- **가장 권장되는 종료 방법**
- JSR-250 표준 어노테이션
- ApplicationContext가 종료될 때 자동 호출
- 리소스 정리 로직 작성

**예제**:
```java
@Component
public class MyBean {
    private Connection connection;
    
    @PostConstruct
    public void init() {
        connection = createConnection();
    }
    
    @PreDestroy
    public void cleanup() {
        System.out.println("10. @PreDestroy 호출");
        // 리소스 정리
        if (connection != null) {
            connection.close();
        }
    }
}
```

**사용 사례**:
- 데이터베이스 연결 종료
- 파일 스트림 닫기
- 스레드 풀 종료
- 캐시 정리

**⚠️ 주의**: 
- `@PreDestroy`는 싱글톤 Bean에서만 호출됨
- 프로토타입 Bean은 호출되지 않음
- `ApplicationContext.close()` 또는 `ConfigurableApplicationContext.shutdown()` 호출 시 실행

---

##### 1️⃣1️⃣ DisposableBean.destroy()

**시점**: @PreDestroy 이후

**특징**:
- `DisposableBean` 인터페이스 구현 필요
- Spring 전용 인터페이스
- `@PreDestroy`와 동일한 목적이지만 Spring에 종속적

**예제**:
```java
@Component
public class MyBean implements DisposableBean {
    @Override
    public void destroy() throws Exception {
        System.out.println("11. destroy() 호출");
        // 리소스 정리
    }
}
```

**⚠️ 권장사항**: `@PreDestroy` 사용 권장 (표준이므로)

---

##### 1️⃣2️⃣ 커스텀 destroy-method

**시점**: destroy() 이후

**특징**:
- `@Bean(destroyMethod = "methodName")` 또는 XML 설정
- 임의의 메서드명 사용 가능
- 반환 타입은 `void`, 파라미터 없음

**예제**:
```java
@Configuration
public class AppConfig {
    @Bean(destroyMethod = "customDestroy")
    public MyBean myBean() {
        return new MyBean();
    }
}

public class MyBean {
    public void customDestroy() {
        System.out.println("12. 커스텀 destroy-method 호출");
    }
}
```

**⚠️ 주의**: 
- `@Bean(destroyMethod = "")`로 빈 문자열 지정 시 destroy 메서드 비활성화
- 자동으로 `close()` 또는 `shutdown()` 메서드를 찾아서 호출하는 경우가 있음

---

#### 📊 전체 생명주기 예제

```java
@Component
public class CompleteLifecycleBean 
    implements BeanNameAware, 
               BeanFactoryAware, 
               ApplicationContextAware,
               InitializingBean,
               DisposableBean {
    
    @Autowired
    private UserService userService;
    
    @Value("${app.name}")
    private String appName;
    
    // 1. 생성자
    public CompleteLifecycleBean() {
        System.out.println("1. Constructor 호출");
    }
    
    // 2. 의존성 주입은 자동으로 수행됨
    
    // 3. BeanNameAware
    @Override
    public void setBeanName(String name) {
        System.out.println("3. Bean 이름: " + name);
    }
    
    // 4. BeanFactoryAware
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        System.out.println("4. BeanFactory 주입");
    }
    
    // 5. ApplicationContextAware
    @Override
    public void setApplicationContext(ApplicationContext context) {
        System.out.println("5. ApplicationContext 주입");
    }
    
    // 6. @PostConstruct
    @PostConstruct
    public void postConstruct() {
        System.out.println("6. @PostConstruct 호출");
    }
    
    // 7. InitializingBean
    @Override
    public void afterPropertiesSet() {
        System.out.println("7. afterPropertiesSet 호출");
    }
    
    // 8. 커스텀 init-method는 @Bean 설정에서 지정
    
    // 9. Bean 사용
    public void doSomething() {
        System.out.println("9. Bean 사용 중");
    }
    
    // 10. @PreDestroy
    @PreDestroy
    public void preDestroy() {
        System.out.println("10. @PreDestroy 호출");
    }
    
    // 11. DisposableBean
    @Override
    public void destroy() {
        System.out.println("11. destroy() 호출");
    }
    
    // 12. 커스텀 destroy-method는 @Bean 설정에서 지정
}
```

**실행 순서 출력**:
```
1. Constructor 호출
3. Bean 이름: completeLifecycleBean
4. BeanFactory 주입
5. ApplicationContext 주입
6. @PostConstruct 호출
7. afterPropertiesSet 호출
9. Bean 사용 중
10. @PreDestroy 호출
11. destroy() 호출
```

---

#### 🎯 실무 권장사항

##### ✅ 권장하는 방법

1. **초기화**: `@PostConstruct` 사용
   - 표준 어노테이션
   - 코드 간결
   - 인터페이스 구현 불필요

2. **종료**: `@PreDestroy` 사용
   - 표준 어노테이션
   - 리소스 정리 명확

##### ⚠️ 주의사항

1. **생성자에서 의존성 사용 금지**
   ```java
   // ❌ 나쁜 예
   public MyBean(@Autowired UserService userService) {
       userService.doSomething();  // 아직 완전히 초기화되지 않음
   }
   
   // ✅ 좋은 예
   @PostConstruct
   public void init() {
       userService.doSomething();  // 모든 의존성 주입 완료 후
   }
   ```

2. **순환 참조 (Circular Dependency) 주의**

   순환 참조는 Bean A가 Bean B를 필요로 하고, Bean B가 다시 Bean A를 필요로 하는 상황입니다.

   **순환 참조 예시**:
   ```java
   @Service
   public class UserService {
       @Autowired
       private OrderService orderService;  // UserService → OrderService
   }
   
   @Service
   public class OrderService {
       @Autowired
       private UserService userService;  // OrderService → UserService
   }
   ```

   **생명주기 관점에서의 순환 참조**:
   
   - **생성자 주입**: 순환 참조가 발생하면 **즉시 에러 발생**
     ```java
     // ❌ 생성자 주입 - 순환 참조 에러
     @Service
     public class UserService {
         private final OrderService orderService;
         
         public UserService(OrderService orderService) {
             this.orderService = orderService;  // OrderService 생성 시도
         }
     }
     
     @Service
     public class OrderService {
         private final UserService userService;
         
         public OrderService(UserService userService) {
             this.userService = userService;  // UserService 생성 시도
             // → BeanCurrentlyInCreationException 발생!
         }
     }
     ```
     **에러 메시지**:
     ```
     Error creating bean with name 'userService': 
     Requested bean is currently in creation: Is there an unresolvable circular reference?
     ```

   - **필드 주입**: Spring이 **지연 초기화(Lazy Initialization)**로 해결 시도
     ```java
     // ⚠️ 필드 주입 - 순환 참조 허용 (하지만 권장하지 않음)
     @Service
     public class UserService {
         @Autowired
         private OrderService orderService;
     }
     
     @Service
     public class OrderService {
         @Autowired
         private UserService userService;
     }
     ```
     **동작 방식**:
     1. UserService 인스턴스 생성 (생성자 호출)
     2. UserService 필드 주입 시도 → OrderService 필요
     3. OrderService 인스턴스 생성 (생성자 호출)
     4. OrderService 필드 주입 시도 → UserService 필요
     5. Spring이 **프록시 객체**를 먼저 주입하여 순환 해결
     6. 이후 실제 객체로 교체

   **Spring의 순환 참조 해결 메커니즘**:
   
   Spring은 **3단계 캐싱**을 사용하여 순환 참조를 해결합니다:
   
   ```
   1단계: Singleton Objects (완전히 초기화된 Bean)
   2단계: Early Singleton Objects (초기화 중인 Bean - 프록시)
   3단계: Singleton Factories (Bean 생성 팩토리)
   ```
   
   **해결 과정**:
   ```java
   // 1. UserService 생성 시작
   UserService userService = new UserService();  // 생성자 호출
   // → Early Singleton Objects에 저장 (아직 완전히 초기화 안 됨)
   
   // 2. UserService 필드 주입 시도 → OrderService 필요
   // 3. OrderService 생성 시작
   OrderService orderService = new OrderService();  // 생성자 호출
   // → Early Singleton Objects에 저장
   
   // 4. OrderService 필드 주입 시도 → UserService 필요
   // 5. Early Singleton Objects에서 UserService 프록시 반환
   orderService.userService = userService;  // 프록시 주입
   
   // 6. OrderService 초기화 완료
   // → Singleton Objects로 이동
   
   // 7. UserService 필드 주입 완료
   userService.orderService = orderService;  // 실제 객체 주입
   
   // 8. UserService 초기화 완료
   // → Singleton Objects로 이동
   ```

   **순환 참조 해결 방법**:
   
   1. **설계 개선 (권장)**: 순환 참조 자체를 제거
      ```java
      // ✅ 좋은 예: 중간 서비스 도입
      @Service
      public class UserService {
          @Autowired
          private UserOrderService userOrderService;  // 중간 서비스
      }
      
      @Service
      public class OrderService {
          @Autowired
      private UserOrderService userOrderService;  // 중간 서비스
      }
      
      @Service
      public class UserOrderService {
          @Autowired
          private UserService userService;
          
          @Autowired
          private OrderService orderService;
      }
      ```

   2. **@Lazy 사용**: 지연 초기화로 순환 참조 해결
      ```java
      @Service
      public class UserService {
          @Autowired
          @Lazy  // 프록시로 주입, 실제 사용 시점에 초기화
          private OrderService orderService;
      }
      
      @Service
      public class OrderService {
          @Autowired
          private UserService userService;
      }
      ```
      **동작**:
      - `@Lazy`가 붙은 Bean은 프록시로 주입됨
      - 실제 메서드 호출 시점에 Bean 초기화
      - 순환 참조 해결

   3. **Setter 주입 사용**: 생성자 주입 대신 Setter 주입
      ```java
      @Service
      public class UserService {
          private OrderService orderService;
          
          @Autowired
          public void setOrderService(OrderService orderService) {
              this.orderService = orderService;
          }
      }
      ```
      **주의**: Setter 주입은 불변성 보장 불가 → 권장하지 않음

   4. **ApplicationContext 직접 조회**: 필요할 때만 조회
      ```java
      @Service
      public class UserService implements ApplicationContextAware {
          private ApplicationContext context;
          
          @Override
          public void setApplicationContext(ApplicationContext context) {
              this.context = context;
          }
          
          public void doSomething() {
              // 필요할 때만 조회
              OrderService orderService = context.getBean(OrderService.class);
          }
      }
      ```

   **순환 참조와 생명주기**:
   
   순환 참조가 있는 경우 생명주기는 다음과 같이 진행됩니다:
   
   ```
   UserService 생성자 호출
   ↓
   UserService 필드 주입 시도 → OrderService 필요
   ↓
   OrderService 생성자 호출
   ↓
   OrderService 필드 주입 시도 → UserService 필요
   ↓
   Spring이 Early Singleton Objects에서 UserService 프록시 반환
   ↓
   OrderService 필드 주입 완료
   ↓
   OrderService @PostConstruct 호출
   ↓
   OrderService 초기화 완료
   ↓
   UserService 필드 주입 완료 (실제 OrderService 객체)
   ↓
   UserService @PostConstruct 호출
   ↓
   UserService 초기화 완료
   ```

   **⚠️ 주의사항**:
   - 순환 참조는 **코드 냄새(code smell)**입니다
   - 가능하면 설계를 개선하여 순환 참조를 제거하세요
   - `@Lazy`는 임시 해결책일 뿐, 근본적인 해결책이 아닙니다
   - 생성자 주입을 사용하면 순환 참조를 조기에 발견할 수 있습니다

   **실무 예제: 순환 참조 발생 케이스**:
   
   ```java
   // ❌ 나쁜 예: 순환 참조
   @Service
   public class UserService {
       @Autowired
       private OrderService orderService;
       
       public void createUser() {
           // ...
       }
   }
   
   @Service
   public class OrderService {
       @Autowired
       private UserService userService;
       
       public void createOrder() {
           // ...
       }
   }
   
   // ✅ 좋은 예: 이벤트로 순환 참조 해결
   @Service
   public class UserService {
       @Autowired
       private ApplicationEventPublisher eventPublisher;
       
       public void createUser() {
           // 사용자 생성
           eventPublisher.publishEvent(new UserCreatedEvent(user));
       }
   }
   
   @Service
   public class OrderService {
       @EventListener
       public void handleUserCreated(UserCreatedEvent event) {
           // 이벤트로 처리 → 순환 참조 없음
       }
   }
   ```

3. **예외 처리**
   ```java
   @PostConstruct
   public void init() {
       try {
           // 초기화 로직
       } catch (Exception e) {
           // 예외 처리 필수
           // 예외 발생 시 Bean 생성 실패
       }
   }
   ```

4. **프로토타입 Bean**
   - 프로토타입 Bean은 `@PreDestroy`가 호출되지 않음
   - 수동으로 리소스 정리 필요

---

#### 📝 요약

| 단계 | 방법 | 특징 | 권장도 |
|------|------|------|--------|
| 초기화 | `@PostConstruct` | 표준, 간결 | ⭐⭐⭐ |
| 초기화 | `InitializingBean` | Spring 전용 | ⭐⭐ |
| 초기화 | `init-method` | 유연함 | ⭐ |
| 종료 | `@PreDestroy` | 표준, 간결 | ⭐⭐⭐ |
| 종료 | `DisposableBean` | Spring 전용 | ⭐⭐ |
| 종료 | `destroy-method` | 유연함 | ⭐ |

---

### 10.2 순환 참조 (Circular Dependency) 심화

#### 🔄 순환 참조란?

**순환 참조(Circular Dependency)**는 두 개 이상의 Bean이 서로를 의존하는 상황입니다.

```
A → B → A  (2-way 순환)
A → B → C → A  (3-way 순환)
```

#### 📊 주입 방식별 순환 참조 처리

| 주입 방식 | 순환 참조 처리 | 에러 발생 | 권장도 |
|-----------|---------------|----------|--------|
| **생성자 주입** | ❌ 즉시 에러 | ✅ BeanCurrentlyInCreationException | ⭐⭐⭐ (조기 발견) |
| **필드 주입** | ⚠️ 3단계 캐싱으로 해결 | ❌ 숨겨짐 | ⭐ (권장 안 함) |
| **Setter 주입** | ⚠️ 3단계 캐싱으로 해결 | ❌ 숨겨짐 | ⭐⭐ |
| **@Lazy** | ✅ 프록시로 해결 | ❌ 없음 | ⭐⭐ (임시 해결책) |

#### 🔍 Spring의 3단계 캐싱 메커니즘

Spring은 Bean 생성을 위해 **3단계 캐싱**을 사용합니다:

```
┌─────────────────────────────────────┐
│ 1. Singleton Objects                 │
│    (완전히 초기화된 Bean)             │
└─────────────────────────────────────┘
           ↑
           │ 초기화 완료 후 이동
           │
┌─────────────────────────────────────┐
│ 2. Early Singleton Objects          │
│    (초기화 중인 Bean - 프록시)       │
│    ← 순환 참조 해결의 핵심!         │
└─────────────────────────────────────┘
           ↑
           │ 생성 시작 시 저장
           │
┌─────────────────────────────────────┐
│ 3. Singleton Factories              │
│    (Bean 생성 팩토리)                │
└─────────────────────────────────────┘
```

**동작 예시**:

```java
@Service
public class ServiceA {
    @Autowired
    private ServiceB serviceB;
}

@Service
public class ServiceB {
    @Autowired
    private ServiceA serviceA;
}
```

**생명주기 단계별 동작**:

```
1. ServiceA 생성자 호출
   → Early Singleton Objects에 ServiceA 저장 (프록시)

2. ServiceA 필드 주입 시도
   → ServiceB 필요

3. ServiceB 생성자 호출
   → Early Singleton Objects에 ServiceB 저장 (프록시)

4. ServiceB 필드 주입 시도
   → ServiceA 필요
   → Early Singleton Objects에서 ServiceA 프록시 반환 ✅

5. ServiceB 필드 주입 완료
   → ServiceB @PostConstruct 호출
   → ServiceB 초기화 완료
   → Singleton Objects로 이동

6. ServiceA 필드 주입 완료 (실제 ServiceB 객체)
   → ServiceA @PostConstruct 호출
   → ServiceA 초기화 완료
   → Singleton Objects로 이동
```

#### ⚠️ 생성자 주입에서 순환 참조

**생성자 주입은 순환 참조를 허용하지 않습니다**:

```java
// ❌ 순환 참조 에러 발생
@Service
public class ServiceA {
    private final ServiceB serviceB;
    
    public ServiceA(ServiceB serviceB) {
        this.serviceB = serviceB;  // ServiceB 생성 필요
    }
}

@Service
public class ServiceB {
    private final ServiceA serviceA;
    
    public ServiceB(ServiceA serviceA) {
        this.serviceA = serviceA;  // ServiceA 생성 필요
        // → BeanCurrentlyInCreationException!
    }
}
```

**에러 메시지**:
```
Error creating bean with name 'serviceA': 
Requested bean is currently in creation: 
Is there an unresolvable circular reference?
```

**왜 생성자 주입은 순환 참조를 허용하지 않나?**

- 생성자는 **객체 생성 시점에 즉시 호출**되어야 함
- 생성자 파라미터로 필요한 Bean이 없으면 객체 생성 불가
- Early Singleton Objects에 저장하기 전에 생성자 호출 필요
- 따라서 순환 참조 해결 불가능

#### ✅ 순환 참조 해결 방법

##### 방법 1: 설계 개선 (가장 권장 ⭐⭐⭐)

**문제**: 두 서비스가 서로 직접 의존

```java
// ❌ 나쁜 예: 순환 참조
@Service
public class UserService {
    @Autowired
    private OrderService orderService;
}

@Service
public class OrderService {
    @Autowired
    private UserService userService;
}
```

**해결책 1-1: 중간 서비스 도입**

```java
// ✅ 좋은 예: 중간 서비스
@Service
public class UserService {
    // OrderService 직접 의존 제거
}

@Service
public class OrderService {
    // UserService 직접 의존 제거
}

@Service
public class UserOrderService {
    @Autowired
    private UserService userService;
    
    @Autowired
    private OrderService orderService;
    
    // 두 서비스를 조합하여 사용
}
```

**해결책 1-2: 이벤트 기반 아키텍처**

```java
// ✅ 좋은 예: 이벤트로 결합도 낮춤
@Service
public class UserService {
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    public void createUser(User user) {
        // 사용자 생성
        eventPublisher.publishEvent(new UserCreatedEvent(user));
    }
}

@Service
public class OrderService {
    @EventListener
    public void handleUserCreated(UserCreatedEvent event) {
        // 이벤트로 처리 → 순환 참조 없음
    }
}
```

**해결책 1-3: 인터페이스 분리**

```java
// ✅ 좋은 예: 인터페이스로 의존 역전
public interface UserRepository {
    User findById(Long id);
}

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;  // 인터페이스에 의존
}

@Service
public class OrderService {
    @Autowired
    private UserRepository userRepository;  // UserService 대신 인터페이스 사용
}
```

##### 방법 2: @Lazy 사용 (임시 해결책 ⭐⭐)

**@Lazy는 프록시를 주입하여 순환 참조를 해결합니다**:

```java
@Service
public class ServiceA {
    @Autowired
    @Lazy  // 프록시로 주입
    private ServiceB serviceB;
    
    public void methodA() {
        serviceB.methodB();  // 실제 호출 시점에 ServiceB 초기화
    }
}

@Service
public class ServiceB {
    @Autowired
    private ServiceA serviceA;  // @Lazy 불필요 (한쪽만 있으면 됨)
}
```

**동작 원리**:

1. ServiceA 생성 시 ServiceB는 **프록시 객체**로 주입
2. ServiceB 생성 시 ServiceA는 **실제 객체**로 주입 가능 (이미 생성됨)
3. ServiceA.methodA() 호출 시 ServiceB 프록시가 실제 메서드 호출
4. 이 시점에 ServiceB가 초기화됨 (지연 초기화)

**⚠️ 주의사항**:
- `@Lazy`는 **임시 해결책**일 뿐, 근본적인 해결책이 아님
- 프록시 오버헤드 발생
- 디버깅이 어려워질 수 있음
- 가능하면 설계를 개선하는 것이 좋음

##### 방법 3: Setter 주입 (비권장 ⭐)

```java
@Service
public class ServiceA {
    private ServiceB serviceB;
    
    @Autowired
    public void setServiceB(ServiceB serviceB) {
        this.serviceB = serviceB;  // Setter로 주입
    }
}

@Service
public class ServiceB {
    @Autowired
    private ServiceA serviceA;
}
```

**단점**:
- 불변성 보장 불가 (`final` 사용 불가)
- 테스트 어려움
- 권장하지 않음

#### 🧪 순환 참조 테스트

**순환 참조 감지 테스트**:

```java
@SpringBootTest
class CircularDependencyTest {
    
    @Test
    void testCircularDependency() {
        // 생성자 주입 시 순환 참조는 즉시 에러 발생
        // 필드 주입 시 순환 참조는 허용되지만 경고 로그 출력
    }
}
```

**Spring Boot 2.6+ 순환 참조 기본 정책**:

- Spring Boot 2.6부터는 **순환 참조가 기본적으로 허용되지 않음**
- `spring.main.allow-circular-references=true` 설정 필요

```properties
# application.properties
spring.main.allow-circular-references=true
```

**⚠️ 권장사항**: 이 설정을 사용하지 말고, 설계를 개선하세요!

#### 📊 순환 참조 해결 방법 비교

| 방법 | 장점 | 단점 | 권장도 |
|------|------|------|--------|
| **설계 개선** | 근본적 해결, 유지보수성 ↑ | 설계 변경 필요 | ⭐⭐⭐ |
| **@Lazy** | 빠른 해결 | 임시 해결책, 프록시 오버헤드 | ⭐⭐ |
| **Setter 주입** | 순환 참조 해결 | 불변성 보장 불가 | ⭐ |
| **allow-circular-references** | 설정만으로 해결 | 코드 냄새, 권장 안 함 | ❌ |

#### 🎯 실무 권장사항

1. **생성자 주입 사용**: 순환 참조를 조기에 발견
2. **설계 개선 우선**: 순환 참조 자체를 제거
3. **@Lazy는 임시 해결책**: 근본적인 해결책이 아님
4. **이벤트 기반 아키텍처**: 결합도 낮추기
5. **인터페이스 활용**: 의존 역전 원칙 적용

#### 📝 순환 참조 체크리스트

- [ ] 생성자 주입을 사용하여 순환 참조 조기 발견
- [ ] 순환 참조 발생 시 설계 개선 검토
- [ ] `@Lazy` 사용 시 임시 해결책임을 인지
- [ ] 이벤트나 인터페이스로 결합도 낮추기
- [ ] `allow-circular-references` 설정 사용 지양

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
