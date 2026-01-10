# 🛡️ CSRF & CORS 방어 방법 상세 정리
> CSRF : Cross Site Request Forgery
> CORS : Cross Origin Resource Sharing
> 이번 정리는 **"어떻게 막는가"** 에 집중한다.
> 단순 나열이 아니라 **왜 이 방법이 유효한지**, **어디서 동작하는지**까지 포함한다.

---

## 0️⃣ 방어 관점부터 다시 정리

| 구분     | CSRF             | CORS                |
| ------ | ---------------- | ------------------- |
| 본질     | 서버가 요청 의도를 구분 못함 | 브라우저의 응답 접근 통제      |
| 방어 위치  | **서버**           | **서버 설정 + 브라우저 판단** |
| 핵심 키워드 | 요청 검증            | 응답 헤더               |

---

## 1️⃣ CSRF 방어 전략 (핵심 파트)

### CSRF 방어의 기본 원칙

> **공격자는 쿠키는 보낼 수 있어도, 서버가 발급한 비밀 값은 모른다**

이 전제를 기반으로 모든 방어 기법이 설계된다.

---

## 2️⃣ CSRF Token 방식 (가장 정석, 가장 강력)

### 개념

* 서버가 **예측 불가능한 토큰** 생성
* 사용자 세션과 바인딩
* 요청 시 함께 전송

```text
쿠키(자동) + 토큰(수동) = 정상 요청
```

---

### 동작 흐름

```
1. 로그인 성공
2. 서버가 CSRF Token 생성
3. HTML 또는 API 응답에 토큰 포함
4. 클라이언트가 요청 시 토큰 전달
5. 서버에서 검증
```

---

### 왜 안전한가?

* 공격자는 **쿠키는 보낼 수 있지만**
* 토큰 값은 알 수 없음 (SOP-SameOriginPolicy)

👉 위조 불가

---

### 실무 예시 (Spring 개념)

```text
X-CSRF-TOKEN: a8f3d9c1...
```

* 보통 Header로 전달
* POST / PUT / DELETE 필수

---

## 3️⃣ Double Submit Cookie (SPA에서 자주 사용)

### 개념

* 토큰을

    * 쿠키에도 저장
    * 요청 바디/헤더에도 저장

---

### 검증 방식

```text
Cookie 값 == Header 값
```

---

### 장점 / 단점

| 장점        | 단점       |
| --------- | -------- |
| 서버 세션 불필요 | XSS에 취약  |
| SPA에 적합   | HTTPS 필수 |

---

## 4️⃣ SameSite Cookie (브라우저 레벨 방어)

### 개념

* 크로스 사이트 요청 시 쿠키 전송 제한

---

### 옵션 상세

| 옵션     | 동작                |
| ------ | ----------------- |
| Strict | 모든 크로스 사이트 요청 차단  |
| Lax    | GET 일부 허용 (기본값)   |
| None   | 전부 허용 (Secure 필수) |

---

### 왜 효과적인가?

* CSRF는 **쿠키 자동 전송**이 전제
* SameSite가 이 전제를 깨뜨림

---

### 한계

* 구형 브라우저
* OAuth / 외부 로그인 흐름

👉 단독 방어 ❌

---

## 5️⃣ Origin / Referer 검증

### 개념

* 요청 헤더의 출처 확인

```http
Origin: https://client.com
Referer: https://client.com/page
```

---

### 평가

| 항목 | 내용         |
| -- | ---------- |
| 장점 | 구현 간단      |
| 단점 | 일부 환경에서 누락 |
| 위치 | 보조 수단      |

---

## 6️⃣ GET 요청으로 상태 변경 금지

### 이유

```text
<img>, <a> 태그는 GET만 사용 가능
```

* CSRF 공격 대부분은 GET 악용

---

### 원칙

```text
GET = 조회
POST/PUT/DELETE = 변경
```

---

## 7️⃣ CORS 방어 전략 (개념부터 다르다)

### CORS 방어의 목적

> **"누가 요청하느냐"가 아니라**
> **"이 응답을 JS가 읽어도 되는가"**

---

## 8️⃣ Access-Control-Allow-Origin 설정

### 안전한 설정

```http
Access-Control-Allow-Origin: https://client.com
```

---

### 위험한 설정

```http
Access-Control-Allow-Origin: *
Access-Control-Allow-Credentials: true
```

❌ 인증 정보 탈취 위험

---

## 9️⃣ Credentials 사용 시 주의점 🔥

```http
Access-Control-Allow-Credentials: true
```

### 반드시 함께 지켜야 할 규칙

* Origin을 명시적으로 지정
* `*` 사용 금지

---

## 🔟 Preflight 최적화 (보안 + 성능)

### Access-Control-Max-Age

```http
Access-Control-Max-Age: 3600
```

* OPTIONS 결과 캐싱
* 불필요한 사전 요청 감소

---

## 1️⃣1️⃣ CORS는 CSRF 방어가 아니다 (중요)

### 이유

* CSRF 요청은 JS 없이 가능
* CORS는 JS 접근만 제어

```text
<img>, <form> 요청은 항상 가능
```

---

## 1️⃣2️⃣ 실무 조합 가이드 🔥

### 쿠키 기반 인증 서버

```
CSRF Token + SameSite + Origin 검증
```

---

### SPA + API (JWT)

```
Authorization Header + CORS 허용
```

---

### 쿠키 기반 SPA

```
CORS + Credentials + CSRF Token
```

---

## 1️⃣3️⃣ 정리

> "CSRF는 쿠키 자동 전송을 악용한 공격이기 때문에,
> 서버가 토큰이나 SameSite 쿠키를 통해 요청의 정당성을 검증해야 합니다.
> 반면 CORS는 브라우저가 응답을 JS가 읽을 수 있는지를 결정하는 정책이라
> Access-Control-Allow-Origin과 Credentials 설정이 핵심입니다."

---

## 1️4️⃣ 초정리 한 문장

> **CSRF는 요청을 막고, CORS는 응답 접근을 막는다**

---

