# 🔐 웹 보안 심화 정리

## 1️⃣ SameSite 쿠키 – 브라우저별 실제 동작 차이

### SameSite의 본질

> **"이 쿠키를 크로스 사이트 요청에 자동으로 실어도 되는가"**

CSRF의 핵심 전제인 *쿠키 자동 전송*을 브라우저 레벨에서 통제한다.

---

### 옵션 정리

| 옵션     | 의미                      |
| ------ | ----------------------- |
| Strict | 모든 크로스 사이트 요청에서 쿠키 전송 ❌ |
| Lax    | 일부 안전한 GET 요청만 허용       |
| None   | 모든 요청 허용 (Secure 필수)    |

---

### 브라우저별 실제 기본값 (중요)

| 브라우저    | 기본 동작                  |
| ------- | ---------------------- |
| Chrome  | SameSite=Lax (명시 안 하면) |
| Edge    | SameSite=Lax           |
| Firefox | Lax (점진적 강화)           |
| Safari  | Lax + 추가 추적 방지 정책      |

👉 **명시하지 않으면 Lax로 강제됨**

---

### 실무에서 자주 터지는 문제

```http
Set-Cookie: JSESSIONID=...; SameSite=None
```

❌ Secure 빠짐 → 쿠키 저장 자체가 안 됨 (Chrome)
“Chrome에서는 SameSite=None 쿠키에 Secure가 없으면
보안상 이유로 쿠키를 아예 저장하지 않습니다.
그래서 크로스 사이트 인증 쿠키를 쓰려면 HTTPS + Secure가 필수입니다.”

```http
Set-Cookie: JSESSIONID=...; SameSite=None; Secure
```

✅ HTTPS 필수

---

### 정리

* SameSite는 **CSRF 1차 방어선**
* 하지만 OAuth / 외부 인증 흐름에서는 한계

---

## 2️⃣ XSS(Cross Site Scripting) → CSRF Token 탈취 공격 시나리오

### 핵심 포인트 🔥

> **CSRF 토큰은 XSS 앞에서는 무력화될 수 있다**

---

### 공격 흐름

```
1. 사이트에 XSS 취약점 존재
2. 공격자가 악성 JS 주입
3. JS가 CSRF 토큰 탈취
4. 탈취한 토큰으로 요청 위조
```

---

### 실제 예시

```javascript
fetch('/transfer', {
  method: 'POST',
  headers: {
    'X-CSRF-TOKEN': document.querySelector('meta[name="_csrf"]').content
  },
  body: 'amount=1000000'
});
```

* 토큰은 Same-Origin JS에 노출됨
* 서버는 정상 요청으로 인식

---

### 결론

```text
CSRF Token ≠ XSS 방어
```

👉 **XSS를 막지 못하면 CSRF도 결국 뚫린다**

---

## 3️⃣ OAuth 2.0에서 state 파라미터가 CSRF인 이유

### OAuth의 위험 포인트

```text
Redirect 기반 인증
```

* 외부 사이트 ↔ 우리 서버
* CSRF 공격 최적 환경

---

### 공격 시나리오

```
1. 공격자가 자신의 계정으로 OAuth 인증
2. 발급된 code를 피해자에게 전달
3. 피해자가 링크 클릭
4. 서버는 피해자 계정에 공격자 계정 연결
```

---

### state 파라미터 역할

```text
state = 서버가 만든 임의의 난수
```

* 요청 시작 시 생성
* 콜백 시 동일 값 요구

---

### 왜 CSRF 방어인가?

| 항목     | 이유        |
| ------ | --------- |
| 예측 불가  | 공격자 위조 불가 |
| 요청 바인딩 | 세션 단위 검증  |

👉 **OAuth 전용 CSRF 토큰**

---

## 4️⃣ Spring Security에서 CSRF / CORS 내부 동작 구조

### 전체 필터 체인 개요

```
SecurityFilterChain
 ├─ CorsFilter
 ├─ CsrfFilter
 ├─ AuthenticationFilter
 └─ AuthorizationFilter
```

---

### CSRF Filter 동작

```
1. 요청 도착
2. 상태 변경 메서드인지 확인
3. CSRF 토큰 추출
4. 서버 저장 토큰과 비교
5. 실패 시 403
```

---

### CORS 처리 위치

* **컨트롤러 이전**
* Preflight는 인증 로직 타지 않음

---

### 중요한 포인트

```text
CORS → CSRF → 인증
```

순서가 바뀌면 보안 취약

---

## 5️⃣ 왜 JWT는 CSRF에 상대적으로 안전한가?

### 핵심 이유

> **JWT는 자동 전송되지 않는다**

---

### 쿠키 vs JWT 비교

| 항목      | 쿠키   | JWT    |
| ------- | ---- | ------ |
| 자동 전송   | ⭕    | ❌      |
| 저장 위치   | 브라우저 | JS 메모리 |
| CSRF 위험 | 높음   | 낮음     |

---

### JWT 인증 흐름

```http
Authorization: Bearer eyJhbGciOi...
```

* JS가 직접 넣어야 함
* 공격자는 토큰을 모름

---

### 단, 완전 무적은 아니다

```text
XSS → JWT 탈취 → 계정 탈취
```

👉 JWT는 CSRF 대신 **XSS에 민감**

---

## 6️⃣ 전체 보안 구조 한 장 요약

```
[XSS] ─┬─> CSRF Token 탈취
       └─> JWT 탈취

[CSRF] ──> SameSite / Token / state

[CORS] ──> 브라우저 응답 접근 제어
```

---

## 7️⃣ 실무 설계 가이드 (결론)

### 서버 렌더링

```
쿠키 + CSRF Token + SameSite
```

---

### SPA + API (JWT)

```
JWT + CORS
```

---

### OAuth 포함 서비스

```
state 필수 + SameSite 고려
```

---

## 🎯 최종 한 문장 정리

> **CSRF는 자동 전송 신뢰의 문제고,
> JWT는 그 신뢰 구조를 애초에 사용하지 않기 때문에 상대적으로 안전하다**

---
