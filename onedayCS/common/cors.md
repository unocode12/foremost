# 🌐 CORS (Cross-Origin Resource Sharing)

> **브라우저 보안 정책(SOP)을 보완하기 위한 메커니즘**
> 다른 출처(origin)의 리소스 접근을 서버가 **명시적으로 허용**할 수 있게 해준다.

---

## 1️⃣ Origin(출처)이란?

Origin은 아래 **3가지의 조합**으로 결정된다.

```
Protocol (Scheme) + Host + Port
```

### 예시

| URL                                                  | Origin                     |
| ---------------------------------------------------- | -------------------------- |
| [https://example.com](https://example.com)           | https + example.com + 443  |
| [http://example.com](http://example.com)             | http + example.com + 80    |
| [https://example.com:8080](https://example.com:8080) | https + example.com + 8080 |

👉 하나라도 다르면 **다른 Origin**

---

## 2️⃣ Same-Origin Policy (SOP)

브라우저의 기본 보안 정책

* 다른 Origin의 리소스 접근 **차단**
* 악성 스크립트로부터 사용자 보호

❗ 하지만 실제 서비스에서는

* 프론트엔드 ↔ 백엔드 분리
* 외부 API 호출

➡️ SOP만으로는 한계 → **CORS 등장**

---

## 3️⃣ CORS란?

> **서버가 허용한 경우에만**
> 브라우저가 다른 Origin 요청을 허용하도록 하는 규칙

중요 포인트 🔥

* **브라우저에서만 동작**
* 서버 ↔ 서버 통신에는 적용 ❌

---

## 4️⃣ CORS 동작 흐름 요약

```
브라우저
  ↓ (요청)
다른 Origin 서버
  ↓ (CORS 헤더 포함 응답)
브라우저가 허용 여부 판단
```

👉 CORS는 **차단이 아니라 브라우저가 판단**

---

## 5️⃣ Simple Request (단순 요청)

### 조건 (모두 만족해야 함)

#### ① HTTP Method

* GET
* POST
* HEAD

#### ② 허용된 Header만 사용

* Accept
* Accept-Language
* Content-Language
* Content-Type

#### ③ Content-Type 제한

* application/x-www-form-urlencoded
* multipart/form-data
* text/plain

---

### 동작 방식

```
1. 브라우저가 바로 실제 요청 전송
2. 서버가 CORS 헤더 포함 응답
3. 브라우저가 허용 여부 판단
```

---

## 6️⃣ Preflight Request (사전 요청) 🔥

Simple Request 조건을 **하나라도 벗어나면** 발생

### 대표적인 경우

* PUT, DELETE, PATCH
* Authorization 헤더 사용
* application/json

---

### 동작 흐름

```
1. OPTIONS 요청 (Preflight)
2. 서버가 허용 정보 응답
3. 허용되면 실제 요청 전송
```

---

### Preflight 요청 예시

```
OPTIONS /api/data HTTP/1.1
Origin: https://client.com
Access-Control-Request-Method: POST
Access-Control-Request-Headers: Authorization
```

---

## 7️⃣ 주요 CORS 응답 헤더 정리

### Access-Control-Allow-Origin

```
Access-Control-Allow-Origin: https://client.com
```

* 허용할 Origin
* `*` 사용 시 credentials 불가

---

### Access-Control-Allow-Methods

```
Access-Control-Allow-Methods: GET, POST, PUT, DELETE
```

---

### Access-Control-Allow-Headers

```
Access-Control-Allow-Headers: Authorization, Content-Type
```

---

### Access-Control-Allow-Credentials

```
Access-Control-Allow-Credentials: true
```

* 쿠키, 인증 정보 포함 허용
* 이 경우 `Allow-Origin`에 `*` 사용 ❌

---

### Access-Control-Max-Age

```
Access-Control-Max-Age: 3600
```

* Preflight 결과 캐싱 시간 (초)

---

## 8️⃣ CORS 에러의 본질 ⚠️

❌ 서버 에러 아님
❌ 네트워크 에러 아님

✅ **브라우저 보안 정책 에러**

```text
Access to fetch at ... has been blocked by CORS policy
```

* 서버는 정상 응답
* 브라우저가 결과를 버림

---

## 9️⃣ 서버별 CORS 설정 개념

### 공통 원칙

* 서버가 CORS 헤더를 내려줘야 함
* 클라이언트에서 해결 불가

---

### Spring Boot 개념 예시

```java
@CrossOrigin(origins = "https://client.com")
@RestController
public class ApiController {
}
```

또는

```java
CorsConfiguration config = new CorsConfiguration();
config.addAllowedOrigin("https://client.com");
config.addAllowedMethod("*");
```

---

## 🔟 자주 나오는 오해 정리

| 오해                       | 실제                 |
| ------------------------ | ------------------ |
| Postman에서는 되는데 브라우저만 안 됨 | 정상 (CORS는 브라우저 전용) |
| 프론트에서 해결 가능              | ❌ 서버 설정 필요         |
| OPTIONS 요청은 쓸모없음         | ❌ 보안 핵심            |

---

## 1️⃣1️⃣ 한 줄 요약

> **CORS는 서버가 허용한 범위 내에서만**
> **브라우저가 다른 Origin 요청을 허용하도록 하는 보안 메커니즘**

---

## 📌 암기 포인트

* CORS는 **브라우저 정책**이다
* Preflight는 **OPTIONS 요청**이다
* `Allow-Credentials: true` + `*` ❌
* 서버 ↔ 서버 통신에는 CORS 없다

---
