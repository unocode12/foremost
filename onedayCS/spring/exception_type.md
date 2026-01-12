### GlobalExceptionHandler

#### 흐름

Client
↓
Filter
↓
DispatcherServlet
↓
HandlerMapping
↓
HandlerAdapter
↓
Argument Resolver
↓
Controller Method
↓
Exception 발생 ❌
↓
ExceptionResolver Chain

1. ExceptionHandlerExceptionResolver (@ExceptionHandler)
2. ResponseStatusExceptionResolver
3. DefaultHandlerExceptionResolver
   ↓
   Response

```text
ExceptionResolver Chain 1번에서 GlobalExceptionHandler 처리
```

---

### GlobalExceptionHandler: Exception 처리 표준 (현재 코드 기준)

#### 공통 응답 포맷

`ApiErrorResponse`로 통일한다.

- code: 에러 코드 문자열
- message: 사용자/클라이언트가 볼 메시지
- path: 요청 URI (`HttpServletRequest.getRequestURI()`)
- timestamp: `Instant.now()`
- requestId: `MDC.get("requestId")`
- details: 예외별 부가 정보(Map)

#### 공통 로깅 규칙

`logAtLevel(status, e, request)` 규칙:

- 5xx: `log.error("API error: ...", e)`
- 그 외(주로 4xx): `log.warn("API error: ...", e)`

#### 예외 매핑 표 (예외 → HTTP / code / message / details)

1. **SlowmeException**

- 대상: 도메인/업무 예외(커스텀)
- HTTP: `e.errorCode().httpStatus()`
- code: `e.errorCode().code()`
- message: `e.messageOverride()`가 있으면 그것, 없으면 `e.errorCode().defaultMessage()`
- details: `e.details()` 그대로 반환

2. **MethodArgumentNotValidException** (`@RequestBody` 검증 실패)

- HTTP: 400
- code/message: `ErrorCode.VALIDATION_ERROR`
- details:
    - `fieldErrors`: `{ fieldName: errorMessage }`

3. **ConstraintViolationException** (`@RequestParam/@PathVariable` 검증 실패)

- HTTP: 400
- code/message: `ErrorCode.VALIDATION_ERROR`
- details:
    - `violations`: `{ propertyPath: message }`

4. **Bad Request 계열**
   대상:

- `HttpMessageNotReadableException` (JSON 파싱 실패/요청 바디 읽기 실패)
- `MissingServletRequestParameterException` (필수 파라미터 누락)
- `MethodArgumentTypeMismatchException` (타입 변환 실패)

응답:

- HTTP: 400
- code/message: `ErrorCode.INVALID_REQUEST`
- details: 빈 Map

5. **AuthenticationException**

- HTTP: 401
- code/message: `ErrorCode.UNAUTHORIZED`
- details: 빈 Map

6. **AccessDeniedException**

- HTTP: 403
- code/message: `ErrorCode.FORBIDDEN`
- details: 빈 Map

7. **NoResourceFoundException** (정적 리소스/매핑 리소스 Not Found)

- HTTP: 404
- code/message: `ErrorCode.NOT_FOUND`
- details: 빈 Map

8. **ResponseStatusException**

- HTTP: `e.getStatusCode()`
- code: `"HTTP-" + status.value()`
- message: `e.getReason()` 있으면 사용, 없으면 `status.getReasonPhrase()`
- details: 빈 Map

9. **ErrorResponseException**

- HTTP: `e.getStatusCode()`
- code: `"HTTP-" + status.value()`
- message: `status.getReasonPhrase()` (reason은 사용하지 않음)
- details: 빈 Map

10. **그 외 모든 Exception**

- HTTP: 500
- code/message: `ErrorCode.INTERNAL_ERROR`
- details: 빈 Map

#### 주의/확장 포인트

- `BindException`(주로 `@ModelAttribute` 검증 실패)은 현재 핸들링에 포함되어 있지 않다.
- “404(컨트롤러 매핑 없음)”까지 완전히 통일하려면 `NoHandlerFoundException` 등도 고려한다.
- 에러코드(`ErrorCode`)가 바뀌면 위 표의 `code/message`도 함께 갱신해야 한다.

### MethodArgumentNotValidException

#### 흐름

HTTP Request
↓
DispatcherServlet
↓
HandlerAdapter (RequestMappingHandlerAdapter)
↓
Argument Resolver
↓
Data Binding
↓
Bean Validation (Hibernate Validator)
❌ 검증 실패
↓
MethodArgumentNotValidException 발생
↓
ExceptionResolver 체인
↓
HTTP 400 응답

#### 정리

MethodArgumentNotValidException은
“요청 바인딩 + Bean Validation 단계에서 발생하며
DispatcherServlet의 ExceptionResolver 체인에서 처리된다”

---

### ConstraintViolationException

#### 흐름

HTTP Request
↓
DispatcherServlet
↓
HandlerAdapter (RequestMappingHandlerAdapter)
↓
Argument Resolver
↓
@RequestParam / @PathVariable 바인딩
↓
Bean Validation (Hibernate Validator)
❌ 검증 실패
↓
ConstraintViolationException 발생
↓
ExceptionResolver 체인
↓
GlobalExceptionHandler.handleConstraintViolation()
↓
HTTP 400 응답

#### 발생 케이스

| 대상 어노테이션/위치              | 설명                 |
| --------------------------------- | -------------------- |
| `@RequestParam` / `@PathVariable` | 값 검증 실패 시 발생 |

#### GlobalExceptionHandler 처리

| 항목    | 값                                            |
| ------- | --------------------------------------------- |
| HTTP    | 400                                           |
| code    | `ErrorCode.VALIDATION_ERROR.code()`           |
| message | `ErrorCode.VALIDATION_ERROR.defaultMessage()` |
| details | `violations: { propertyPath: message }`       |

---

### HttpMessageNotReadableException

#### 흐름

HTTP Request (JSON Body)
↓
DispatcherServlet
↓
HandlerAdapter (RequestMappingHandlerAdapter)
↓
HttpMessageConverter (Jackson 등)
❌ JSON 파싱/역직렬화 실패
↓
HttpMessageNotReadableException 발생
↓
ExceptionResolver 체인
↓
GlobalExceptionHandler.handleBadRequest()
↓
HTTP 400 응답

#### 발생 케이스

| 원인             | 예시                  |
| ---------------- | --------------------- |
| JSON 문법 오류   | 쉼표/따옴표 누락      |
| 타입 불일치      | 숫자 필드에 문자열    |
| 필수 구조 불일치 | 객체/배열 형태 불일치 |

#### GlobalExceptionHandler 처리

| 항목    | 값                                           |
| ------- | -------------------------------------------- |
| HTTP    | 400                                          |
| code    | `ErrorCode.INVALID_REQUEST.code()`           |
| message | `ErrorCode.INVALID_REQUEST.defaultMessage()` |
| details | 빈 Map                                       |

---

### MissingServletRequestParameterException

#### 흐름

HTTP Request
↓
DispatcherServlet
↓
HandlerAdapter (RequestMappingHandlerAdapter)
↓
Argument Resolver
❌ required request parameter 누락
↓
MissingServletRequestParameterException 발생
↓
ExceptionResolver 체인
↓
GlobalExceptionHandler.handleBadRequest()
↓
HTTP 400 응답

#### GlobalExceptionHandler 처리

| 항목    | 값                                           |
| ------- | -------------------------------------------- |
| HTTP    | 400                                          |
| code    | `ErrorCode.INVALID_REQUEST.code()`           |
| message | `ErrorCode.INVALID_REQUEST.defaultMessage()` |
| details | 빈 Map                                       |

---

### MethodArgumentTypeMismatchException

#### 흐름

HTTP Request
↓
DispatcherServlet
↓
HandlerAdapter (RequestMappingHandlerAdapter)
↓
Argument Resolver + Type Conversion
❌ 타입 변환 실패 (String → int 등)
↓
MethodArgumentTypeMismatchException 발생
↓
ExceptionResolver 체인
↓
GlobalExceptionHandler.handleBadRequest()
↓
HTTP 400 응답

#### 예시

| 파라미터 | 기대 타입 | 입력  | 결과      |
| -------- | --------- | ----- | --------- |
| `page`   | int       | `abc` | 변환 실패 |

#### GlobalExceptionHandler 처리

| 항목    | 값                                           |
| ------- | -------------------------------------------- |
| HTTP    | 400                                          |
| code    | `ErrorCode.INVALID_REQUEST.code()`           |
| message | `ErrorCode.INVALID_REQUEST.defaultMessage()` |
| details | 빈 Map                                       |

---

### AuthenticationException

#### 흐름

HTTP Request
↓
Filter Chain (Spring Security)
↓
인증 시도
❌ 인증 실패/미인증
↓
AuthenticationException 발생
↓
ExceptionResolver 체인 (또는 Security의 엔트리포인트 처리)
↓
GlobalExceptionHandler.handleAuthentication()
↓
HTTP 401 응답

#### GlobalExceptionHandler 처리

| 항목    | 값                                        |
| ------- | ----------------------------------------- |
| HTTP    | 401                                       |
| code    | `ErrorCode.UNAUTHORIZED.code()`           |
| message | `ErrorCode.UNAUTHORIZED.defaultMessage()` |
| details | 빈 Map                                    |

---

### AccessDeniedException

#### 흐름

HTTP Request
↓
Filter Chain (Spring Security)
↓
인가(권한) 체크
❌ 권한 부족
↓
AccessDeniedException 발생
↓
ExceptionResolver 체인 (또는 Security의 AccessDeniedHandler 처리)
↓
GlobalExceptionHandler.handleAccessDenied()
↓
HTTP 403 응답

#### GlobalExceptionHandler 처리

| 항목    | 값                                     |
| ------- | -------------------------------------- |
| HTTP    | 403                                    |
| code    | `ErrorCode.FORBIDDEN.code()`           |
| message | `ErrorCode.FORBIDDEN.defaultMessage()` |
| details | 빈 Map                                 |

---

### NoResourceFoundException

#### 흐름

HTTP Request
↓
DispatcherServlet
↓
정적 리소스/리소스 핸들러 탐색
❌ 리소스 없음
↓
NoResourceFoundException 발생
↓
ExceptionResolver 체인
↓
GlobalExceptionHandler.handleNotFound()
↓
HTTP 404 응답

#### GlobalExceptionHandler 처리

| 항목    | 값                                     |
| ------- | -------------------------------------- |
| HTTP    | 404                                    |
| code    | `ErrorCode.NOT_FOUND.code()`           |
| message | `ErrorCode.NOT_FOUND.defaultMessage()` |
| details | 빈 Map                                 |

---

### ResponseStatusException

#### 흐름

Controller/Service
↓
`throw new ResponseStatusException(...)`
↓
ResponseStatusException 발생
↓
ExceptionResolver 체인
↓
GlobalExceptionHandler.handleResponseStatus()
↓
지정한 HTTP status로 응답

#### GlobalExceptionHandler 처리

| 항목    | 값                                                      |
| ------- | ------------------------------------------------------- |
| HTTP    | `e.getStatusCode()`                                     |
| code    | `"HTTP-" + status.value()`                              |
| message | `e.getReason()` 우선, 없으면 `status.getReasonPhrase()` |
| details | 빈 Map                                                  |

---

### ErrorResponseException

#### 흐름

Spring 내부(또는 프레임워크 레벨)
↓
ErrorResponseException 발생
↓
ExceptionResolver 체인
↓
GlobalExceptionHandler.handleErrorResponseException()
↓
HTTP status로 응답

#### GlobalExceptionHandler 처리

| 항목    | 값                         |
| ------- | -------------------------- |
| HTTP    | `e.getStatusCode()`        |
| code    | `"HTTP-" + status.value()` |
| message | `status.getReasonPhrase()` |
| details | 빈 Map                     |

---

### SlowmeException (커스텀/도메인 예외)

#### 흐름

Controller/Service
↓
`throw new SlowmeException(ErrorCode, ...)`
↓
SlowmeException 발생
↓
ExceptionResolver 체인
↓
GlobalExceptionHandler.handleSlowmeException()
↓
ErrorCode 기반 HTTP 응답

#### GlobalExceptionHandler 처리

| 항목    | 값                                                     |
| ------- | ------------------------------------------------------ |
| HTTP    | `e.errorCode().httpStatus()`                           |
| code    | `e.errorCode().code()`                                 |
| message | `messageOverride` 있으면 사용, 없으면 `defaultMessage` |
| details | `e.details()`                                          |

---

### Exception (그 외 모든 예외)

#### 흐름

어느 레이어에서든 예외 발생
↓
Exception 발생
↓
ExceptionResolver 체인
↓
GlobalExceptionHandler.handleUnexpected()
↓
HTTP 500 응답

#### GlobalExceptionHandler 처리

| 항목    | 값                                          |
| ------- | ------------------------------------------- |
| HTTP    | 500                                         |
| code    | `ErrorCode.INTERNAL_ERROR.code()`           |
| message | `ErrorCode.INTERNAL_ERROR.defaultMessage()` |
| details | 빈 Map                                      |
