[SSL Offloading 기술적 정리]
SSL Offloading은 클라이언트와 서버 간 HTTPS 통신에서 발생하는 SSL/TLS 암·복호화 처리를 애플리케이션 서버가 아닌 네트워크 경계 계층에서 수행하도록 분리하는 아키텍처 기법이다. 일반적으로 L7 Load Balancer(ALB), Reverse Proxy(Nginx, HAProxy), API Gateway, CDN이 TLS 종료 지점(TLS Termination Point) 역할을 수행한다. 클라이언트는 해당 계층과 TLS 핸드셰이크를 수행하며, 인증서 검증, 공개키 기반 키 교환, 대칭키 암호화 통신이 이 지점에서 완료된다.
TLS 종료 이후 프록시는 수신한 암호화 트래픽을 복호화하고, 내부 네트워크로는 HTTP 또는 내부 TLS(Re-encryption) 방식으로 요청을 전달한다. 이로 인해 애플리케이션 서버는 TLS 연산을 수행하지 않고 순수 비즈니스 로직 처리에만 집중할 수 있으며, CPU 사용량 감소와 처리량 증가 효과를 얻는다. 특히 다수의 백엔드 서버를 사용하는 환경에서는 인증서 관리와 TLS 설정을 중앙화할 수 있어 운영 복잡도가 크게 감소한다.
SSL Offloading 구조에서 핵심 포인트는 TLS 세션의 종료 위치이다. TLS가 Load Balancer에서 종료될 경우, 애플리케이션 서버는 클라이언트의 원본 TLS 정보를 직접 알 수 없으며, X-Forwarded-Proto, X-Forwarded-For 등의 헤더를 통해 간접적으로 요청 정보를 전달받는다. 이로 인해 IP 기반 보안 로직이나 클라이언트 인증서 기반 인증이 필요한 경우 추가적인 설정이 요구된다.
보안 측면에서 SSL Offloading은 내부 네트워크가 신뢰 구간이라는 전제하에 설계되는 경우가 많다. 내부 통신을 평문 HTTP로 구성하면 성능은 향상되지만, 내부 네트워크 노출 시 보안 위험이 존재한다. 이를 보완하기 위해 Load Balancer에서 TLS를 종료한 후 백엔드 서버로 다시 TLS로 암호화하는 SSL Re-encryption 방식을 사용하기도 한다. 이 경우 암·복호화 비용은 일부 증가하지만 종단 간 암호화에 가까운 보안 수준을 유지할 수 있다.
SSL Offloading은 트래픽 집중 지점에서 암호화 처리를 수행하므로 WAF, Rate Limiting, 인증/인가, 로깅, 모니터링과 같은 보안 및 트래픽 제어 기능과 결합하기 용이하다. 클라우드 환경에서는 ACM과 같은 인증서 관리 서비스와 통합되어 자동 갱신 및 배포가 가능하다.

[트래픽 흐름 도식]
Client
  |
  | HTTPS (TLS Handshake, Encrypted)
  v
+-------------------------------+
| Load Balancer / Proxy / CDN   |
| - TLS Termination             |
| - Certificate Validation     |
| - Decryption / Encryption    |
+-------------------------------+
          |
          | HTTP (or Internal TLS)
          v
+-------------------------------+
| Application Server            |
| - Business Logic              |
| - No TLS Overhead             |
+-------------------------------+

[요약]
SSL Offloading은 TLS 암·복호화 책임을 애플리케이션 서버에서 네트워크 경계 계층으로 이동시켜 성능, 확장성, 운영 효율을 개선하는 설계 방식이며, 내부 통신 보안 요구 수준에 따라 평문 전달 또는 TLS 재암호화 방식을 선택적으로 적용한다.