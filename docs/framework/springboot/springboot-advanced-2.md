# Spring Boot 고급 이론 10가지 (Part 2)

## 📋 목차

1. [Spring MVC 아키텍처와 동작 원리](#1-spring-mvc-아키텍처와-동작-원리)
2. [Bean Scope와 생명주기](#2-bean-scope와-생명주기)
3. [AOP와 Proxy 메커니즘](#3-aop와-proxy-메커니즘)
4. [Transaction Management](#4-transaction-management)
5. [Exception Handling 전략](#5-exception-handling-전략)
6. [Interceptor vs Filter](#6-interceptor-vs-filter)
7. [Argument Resolver와 ReturnValueHandler](#7-argument-resolver와-returnvaluehandler)
8. [HttpMessageConverter](#8-httpmessageconverter)
9. [CORS와 Security 설정](#9-cors와-security-설정)
10. [Testing 전략](#10-testing-전략)

---

## 1. Spring MVC 아키텍처와 동작 원리

### 1.1 요청 처리 흐름

```
HTTP Request
    ↓
DispatcherServlet
    ↓
HandlerMapping (요청 → Handler 찾기)
    ↓
HandlerAdapter (Handler 실행)
    ↓
Controller
    ↓
ModelAndView
    ↓
ViewResolver (View 찾기)
    ↓
View 렌더링
    ↓
HTTP Response
```

### 1.2 DispatcherServlet의 역할

```java
@WebServlet(name = "dispatcher", urlPatterns = "/")
public class DispatcherServlet extends FrameworkServlet {
    // 핵심 컴포넌트들
    private List<HandlerMapping> handlerMappings;
    private List<HandlerAdapter> handlerAdapters;
    private List<ViewResolver> viewResolvers;
    private List<HandlerExceptionResolver> exceptionResolvers;
}
```

**DispatcherServlet의 책임**:
- 요청을 적절한 Handler로 라우팅
- Handler 실행을 위한 Adapter 찾기
- 예외 처리
- View 렌더링

### 1.3 HandlerMapping 전략

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Override
    public void configureHandlerMappings(HandlerMappingRegistry registry) {
        // RequestMappingHandlerMapping (기본)
        // BeanNameUrlHandlerMapping
        // SimpleUrlHandlerMapping
    }
}
```

**우선순위**:
1. `RequestMappingHandlerMapping` - `@RequestMapping` 기반
2. `BeanNameUrlHandlerMapping` - 빈 이름 기반
3. `SimpleUrlHandlerMapping` - URL 패턴 직접 매핑

### 1.4 HandlerAdapter 종류

| Adapter | Handler 타입 |
|---------|-------------|
| `RequestMappingHandlerAdapter` | `@Controller` 메서드 |
| `HttpRequestHandlerAdapter` | `HttpRequestHandler` |
| `SimpleControllerHandlerAdapter` | `Controller` 인터페이스 |

---

## 2. Bean Scope와 생명주기

### 2.1 Spring Bean Scope 종류

| Scope | 설명 | 사용 시나리오 |
|-------|------|--------------|
| **singleton** (기본) | 컨테이너당 1개 인스턴스 | 대부분의 경우 |
| **prototype** | 매번 새 인스턴스 생성 | 상태를 가진 빈 |
| **request** | HTTP 요청당 1개 | 웹 애플리케이션 |
| **session** | HTTP 세션당 1개 | 사용자별 상태 |
| **application** | ServletContext당 1개 | 서블릿 컨텍스트 범위 |
| **websocket** | WebSocket 세션당 1개 | WebSocket 연결 |

### 2.2 Scope 사용 예제

```java
// Singleton (기본)
@Component
public class SingletonBean {
    // 컨테이너당 1개만 존재
}

// Prototype
@Component
@Scope("prototype")
public class PrototypeBean {
    // 매번 새로 생성
}

// Request Scope
@Component
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestScopedBean {
    private String requestId;
    // HTTP 요청마다 새 인스턴스
}

// Session Scope
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class UserPreferences {
    private String theme;
    // 세션마다 별도 인스턴스
}
```

### 2.3 ProxyMode의 필요성

**문제 상황**:
```java
@Service
public class UserService {
    @Autowired
    private RequestScopedBean bean; // Singleton에 Request Scope 주입?
}
```

**해결**: `ScopedProxyMode.TARGET_CLASS`로 프록시 생성
- Singleton 빈에 Request Scope 빈을 주입할 때 프록시로 감싸서 주입
- 실제 사용 시점에 현재 Request의 인스턴스를 반환

### 2.4 커스텀 Scope 구현

```java
public class ThreadScope implements Scope {
    
    private final ThreadLocal<Map<String, Object>> threadLocal = 
        ThreadLocal.withInitial(HashMap::new);
    
    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        Map<String, Object> scope = threadLocal.get();
        return scope.computeIfAbsent(name, k -> objectFactory.getObject());
    }
    
    @Override
    public Object remove(String name) {
        return threadLocal.get().remove(name);
    }
    
    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
        // 정리 로직
    }
}

// 등록
@Configuration
public class ScopeConfig {
    @Bean
    public static CustomScopeConfigurer customScopeConfigurer() {
        CustomScopeConfigurer configurer = new CustomScopeConfigurer();
        configurer.addScope("thread", new ThreadScope());
        return configurer;
    }
}
```

---

## 3. AOP와 Proxy 메커니즘

### 3.1 AOP 개념

**AOP (Aspect-Oriented Programming)**는 횡단 관심사(Cross-cutting Concerns)를 모듈화하는 프로그래밍 패러다임입니다.

**횡단 관심사 예시**:
- 로깅
- 트랜잭션 관리
- 보안
- 성능 모니터링

### 3.2 Spring AOP vs AspectJ

| 항목 | Spring AOP | AspectJ |
|------|-----------|---------|
| **위빙 시점** | 런타임 (프록시) | 컴파일/로드 타임 |
| **대상** | Spring Bean만 | 모든 Java 객체 |
| **성능** | 약간 느림 (프록시 오버헤드) | 빠름 |
| **기능** | 메서드 레벨만 | 필드, 생성자 등 모든 지점 |

### 3.3 Proxy 메커니즘

**JDK Dynamic Proxy** (인터페이스 기반):
```java
public interface UserService {
    void save(User user);
}

@Service
public class UserServiceImpl implements UserService {
    @Override
    public void save(User user) {
        // 구현
    }
}

// Spring이 자동 생성하는 프록시
UserService proxy = (UserService) Proxy.newProxyInstance(
    classLoader,
    new Class[]{UserService.class},
    new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            // Before Advice
            Object result = target.save(args[0]);
            // After Advice
            return result;
        }
    }
);
```

**CGLIB Proxy** (클래스 기반):
```java
@Service
public class UserService { // 인터페이스 없음
    public void save(User user) {
        // 구현
    }
}

// CGLIB이 서브클래스를 생성
public class UserService$$EnhancerBySpringCGLIB extends UserService {
    @Override
    public void save(User user) {
        // Before Advice
        super.save(user);
        // After Advice
    }
}
```

### 3.4 @Aspect 예제

```java
@Aspect
@Component
public class LoggingAspect {
    
    // Pointcut 정의
    @Pointcut("execution(* com.example.service.*.*(..))")
    public void serviceMethods() {}
    
    // Before Advice
    @Before("serviceMethods()")
    public void logBefore(JoinPoint joinPoint) {
        System.out.println("Before: " + joinPoint.getSignature());
    }
    
    // After Returning
    @AfterReturning(pointcut = "serviceMethods()", returning = "result")
    public void logAfterReturning(Object result) {
        System.out.println("Returned: " + result);
    }
    
    // Around Advice
    @Around("serviceMethods()")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("Execution time: " + elapsed + "ms");
        return result;
    }
    
    // After Throwing
    @AfterThrowing(pointcut = "serviceMethods()", throwing = "ex")
    public void logException(JoinPoint joinPoint, Exception ex) {
        System.out.println("Exception: " + ex.getMessage());
    }
}
```

### 3.5 @Transactional의 동작 원리

```java
@Service
public class UserService {
    
    @Transactional
    public void save(User user) {
        // 트랜잭션 프록시가 감싸서 실행
        // 1. 트랜잭션 시작
        // 2. 실제 메서드 실행
        // 3. 커밋 또는 롤백
    }
}
```

**주의사항**: 같은 클래스 내부 메서드 호출 시 프록시가 적용되지 않음
```java
@Service
public class UserService {
    
    public void method1() {
        method2(); // ❌ 프록시 적용 안 됨
    }
    
    @Transactional
    public void method2() {
        // 트랜잭션 적용 안 됨!
    }
}
```

---

## 4. Transaction Management

### 4.1 트랜잭션 전파 (Propagation)

| 전파 속성 | 설명 |
|-----------|------|
| **REQUIRED** (기본) | 기존 트랜잭션이 있으면 참여, 없으면 새로 생성 |
| **REQUIRES_NEW** | 항상 새 트랜잭션 생성 |
| **SUPPORTS** | 트랜잭션이 있으면 참여, 없으면 트랜잭션 없이 실행 |
| **MANDATORY** | 반드시 트랜잭션 필요, 없으면 예외 |
| **NOT_SUPPORTED** | 트랜잭션 없이 실행, 기존 트랜잭션 일시 중지 |
| **NEVER** | 트랜잭션 없이 실행, 있으면 예외 |
| **NESTED** | 중첩 트랜잭션 (Savepoint 사용) |

### 4.2 격리 수준 (Isolation)

| 격리 수준 | Dirty Read | Non-Repeatable Read | Phantom Read |
|-----------|------------|---------------------|--------------|
| **READ_UNCOMMITTED** | 가능 | 가능 | 가능 |
| **READ_COMMITTED** | 불가 | 가능 | 가능 |
| **REPEATABLE_READ** | 불가 | 불가 | 가능 |
| **SERIALIZABLE** | 불가 | 불가 | 불가 |

### 4.3 실전 예제

```java
@Service
public class OrderService {
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private PaymentService paymentService;
    
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        timeout = 30,
        rollbackFor = Exception.class
    )
    public void createOrder(Order order) {
        orderRepository.save(order);
        paymentService.processPayment(order); // REQUIRES_NEW로 실행
    }
}

@Service
public class PaymentService {
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processPayment(Order order) {
        // 독립적인 트랜잭션으로 실행
        // 실패해도 createOrder는 커밋됨
    }
}
```

### 4.4 트랜잭션 동기화

```java
@Service
public class DataService {
    
    @Autowired
    private DataSource dataSource;
    
    @Transactional
    public void method() {
        // 트랜잭션 동기화된 Connection 획득
        Connection conn = DataSourceUtils.getConnection(dataSource);
        // 같은 트랜잭션에서 실행됨
    }
}
```

---

## 5. Exception Handling 전략

### 5.1 @ControllerAdvice

```java
@ControllerAdvice
public class GlobalExceptionHandler {
    
    // 특정 예외 처리
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex) {
        ErrorResponse error = new ErrorResponse("BAD_REQUEST", ex.getMessage());
        return ResponseEntity.badRequest().body(error);
    }
    
    // 여러 예외 처리
    @ExceptionHandler({NullPointerException.class, IndexOutOfBoundsException.class})
    public ResponseEntity<ErrorResponse> handleMultiple(Exception ex) {
        ErrorResponse error = new ErrorResponse("INTERNAL_ERROR", ex.getMessage());
        return ResponseEntity.status(500).body(error);
    }
    
    // 모든 예외 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAll(Exception ex) {
        ErrorResponse error = new ErrorResponse("UNKNOWN_ERROR", ex.getMessage());
        return ResponseEntity.status(500).body(error);
    }
}
```

### 5.2 @ExceptionHandler 우선순위

1. **컨트롤러 내부** `@ExceptionHandler` (최우선)
2. **@ControllerAdvice**의 `@ExceptionHandler`
3. **HandlerExceptionResolver** 구현체

### 5.3 HandlerExceptionResolver

```java
@Component
public class CustomExceptionResolver implements HandlerExceptionResolver {
    
    @Override
    public ModelAndView resolveException(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        
        if (ex instanceof CustomException) {
            response.setStatus(400);
            return new ModelAndView("error/custom");
        }
        return null; // 다음 Resolver로 위임
    }
}
```

### 5.4 ResponseStatusException (Spring 5.3+)

```java
@RestController
public class UserController {
    
    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "User not found"
            );
        }
        return user;
    }
}
```

---

## 6. Interceptor vs Filter

### 6.1 Filter (서블릿 레벨)

```java
@Component
@Order(1)
public class LoggingFilter implements Filter {
    
    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        long start = System.currentTimeMillis();
        
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("Request: " + httpRequest.getRequestURI() + 
                             " took " + elapsed + "ms");
        }
    }
}
```

**특징**:
- 서블릿 컨테이너 레벨에서 동작
- Spring Context 밖에서 실행
- `@Component`로 등록하거나 `FilterRegistrationBean` 사용

### 6.2 Interceptor (Spring MVC 레벨)

```java
@Component
public class AuthInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {
        
        String token = request.getHeader("Authorization");
        if (!isValidToken(token)) {
            response.setStatus(401);
            return false; // 요청 중단
        }
        return true; // 계속 진행
    }
    
    @Override
    public void postHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            ModelAndView modelAndView) throws Exception {
        // Handler 실행 후, View 렌더링 전
    }
    
    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) throws Exception {
        // View 렌더링 완료 후
    }
}

// 등록
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Autowired
    private AuthInterceptor authInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/public/**");
    }
}
```

### 6.3 실행 순서 비교

```
Request
    ↓
Filter 1
    ↓
Filter 2
    ↓
DispatcherServlet
    ↓
Interceptor.preHandle()
    ↓
Controller
    ↓
Interceptor.postHandle()
    ↓
View 렌더링
    ↓
Interceptor.afterCompletion()
    ↓
Filter 2 (역순)
    ↓
Filter 1 (역순)
    ↓
Response
```

### 6.4 언제 무엇을 사용할까?

**Filter 사용**:
- 인코딩 변환
- CORS 처리
- XSS 방어
- 요청/응답 로깅

**Interceptor 사용**:
- 인증/인가
- 로깅 (Handler 정보 필요)
- ModelAndView 수정
- Spring Bean 주입 필요

---

## 7. Argument Resolver와 ReturnValueHandler

### 7.1 Argument Resolver

**Argument Resolver**는 컨트롤러 메서드 파라미터를 바인딩하는 컴포넌트입니다.

**기본 제공 Resolver**:
- `@RequestParam` → `RequestParamMethodArgumentResolver`
- `@PathVariable` → `PathVariableMethodArgumentResolver`
- `@RequestBody` → `RequestResponseBodyMethodProcessor`
- `@ModelAttribute` → `ModelAttributeMethodProcessor`

### 7.2 커스텀 Argument Resolver

```java
// 커스텀 어노테이션
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}

// Resolver 구현
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {
    
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class) &&
               parameter.getParameterType().equals(User.class);
    }
    
    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) throws Exception {
        
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        String userId = request.getHeader("X-User-Id");
        return userService.findById(Long.parseLong(userId));
    }
}

// 등록
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Autowired
    private CurrentUserArgumentResolver currentUserArgumentResolver;
    
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}

// 사용
@RestController
public class UserController {
    
    @GetMapping("/profile")
    public UserProfile getProfile(@CurrentUser User user) {
        return userService.getProfile(user);
    }
}
```

### 7.3 ReturnValueHandler

```java
@Component
public class CustomReturnValueHandler implements HandlerMethodReturnValueHandler {
    
    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        return returnType.getParameterType().equals(ApiResponse.class);
    }
    
    @Override
    public void handleReturnValue(
            Object returnValue,
            MethodParameter returnType,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest) throws Exception {
        
        ApiResponse<?> response = (ApiResponse<?>) returnValue;
        HttpServletResponse httpResponse = webRequest.getNativeResponse(HttpServletResponse.class);
        
        httpResponse.setStatus(response.getStatus());
        httpResponse.setContentType("application/json");
        httpResponse.getWriter().write(objectMapper.writeValueAsString(response));
        
        mavContainer.setRequestHandled(true);
    }
}
```

---

## 8. HttpMessageConverter

### 8.1 개념

**HttpMessageConverter**는 HTTP 요청/응답 본문을 Java 객체로 변환하거나 그 반대로 변환합니다.

### 8.2 기본 제공 Converter

| Converter | Content-Type | 설명 |
|-----------|--------------|------|
| `StringHttpMessageConverter` | `text/*` | String 변환 |
| `MappingJackson2HttpMessageConverter` | `application/json` | JSON 변환 |
| `ByteArrayHttpMessageConverter` | `application/octet-stream` | 바이트 배열 |
| `FormHttpMessageConverter` | `application/x-www-form-urlencoded` | 폼 데이터 |

### 8.3 커스텀 Converter

```java
@Component
public class CsvHttpMessageConverter extends AbstractHttpMessageConverter<Object> {
    
    public CsvHttpMessageConverter() {
        super(new MediaType("text", "csv"));
    }
    
    @Override
    protected boolean supports(Class<?> clazz) {
        return true; // 모든 타입 지원
    }
    
    @Override
    protected Object readInternal(
            Class<?> clazz,
            HttpInputMessage inputMessage) throws IOException {
        // CSV → Java 객체 변환
        return csvParser.parse(inputMessage.getBody());
    }
    
    @Override
    protected void writeInternal(
            Object object,
            HttpOutputMessage outputMessage) throws IOException {
        // Java 객체 → CSV 변환
        String csv = csvWriter.write(object);
        outputMessage.getBody().write(csv.getBytes());
    }
}
```

### 8.4 Content Negotiation

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
            .favorParameter(true)
            .parameterName("format")
            .mediaType("json", MediaType.APPLICATION_JSON)
            .mediaType("xml", MediaType.APPLICATION_XML)
            .defaultContentType(MediaType.APPLICATION_JSON);
    }
}

// 사용: /api/users?format=xml
@RestController
public class UserController {
    
    @GetMapping("/users")
    public List<User> getUsers() {
        return userService.findAll();
        // Accept 헤더나 format 파라미터에 따라 JSON/XML 반환
    }
}
```

---

## 9. CORS와 Security 설정

### 9.1 CORS (Cross-Origin Resource Sharing)

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000", "https://example.com")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

// 또는 @CrossOrigin
@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class ApiController {
    // ...
}
```

### 9.2 Spring Security 기본 설정

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/")
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**")
            );
        return http.build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### 9.3 JWT 인증 구현

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Autowired
    private JwtTokenProvider tokenProvider;
    
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        
        String token = extractToken(request);
        if (token != null && tokenProvider.validateToken(token)) {
            Authentication auth = tokenProvider.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }
    
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

---

## 10. Testing 전략

### 10.1 @SpringBootTest

```java
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    void testCreateUser() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"John\",\"email\":\"john@test.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("John"));
    }
}
```

### 10.2 @WebMvcTest

```java
@WebMvcTest(UserController.class)
class UserControllerWebTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private UserService userService; // Mock 주입
    
    @Test
    void testGetUser() throws Exception {
        when(userService.findById(1L)).thenReturn(new User("John"));
        
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("John"));
    }
}
```

### 10.3 @DataJpaTest

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    void testSave() {
        User user = new User("John", "john@test.com");
        User saved = userRepository.save(user);
        
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("John");
    }
}
```

### 10.4 @MockBean vs @Mock

| 어노테이션 | 사용 위치 | Spring Context |
|-----------|----------|----------------|
| `@MockBean` | `@SpringBootTest`, `@WebMvcTest` | Spring Bean으로 등록 |
| `@Mock` | 단위 테스트 | Mockito Mock 객체 |

### 10.5 TestContainers 활용

```java
@SpringBootTest
@Testcontainers
class IntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Test
    void testWithRealDatabase() {
        // 실제 PostgreSQL 컨테이너와 통합 테스트
    }
}
```

---

## 🎯 핵심 정리

### MVC 아키텍처
- DispatcherServlet이 요청을 라우팅하고 처리
- HandlerMapping, HandlerAdapter, ViewResolver의 역할 이해

### Bean Scope
- Singleton (기본), Prototype, Request, Session 등
- ProxyMode로 Scope 문제 해결

### AOP
- JDK Dynamic Proxy vs CGLIB
- @Aspect로 횡단 관심사 모듈화

### 트랜잭션
- Propagation, Isolation 이해
- 같은 클래스 내부 호출 시 주의

### 예외 처리
- @ControllerAdvice로 전역 예외 처리
- HandlerExceptionResolver로 커스터마이징

### Interceptor vs Filter
- Filter: 서블릿 레벨, 모든 요청
- Interceptor: Spring MVC 레벨, Handler 정보 접근 가능

### Argument Resolver
- 커스텀 파라미터 바인딩
- @CurrentUser 같은 편의 기능 구현

### HttpMessageConverter
- 요청/응답 본문 변환
- Content Negotiation으로 형식 선택

### CORS & Security
- CORS 설정으로 크로스 오리진 요청 허용
- Spring Security로 인증/인가

### Testing
- @SpringBootTest: 통합 테스트
- @WebMvcTest: 컨트롤러 단위 테스트
- @DataJpaTest: 리포지토리 테스트
- TestContainers: 실제 인프라 통합 테스트

---

## 📚 참고

- [Spring MVC 공식 문서](https://docs.spring.io/spring-framework/docs/current/reference/html/web.html)
- [Spring AOP 가이드](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#aop)
- [Spring Security 가이드](https://docs.spring.io/spring-security/reference/index.html)
