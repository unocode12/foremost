package com.unocode.behavioral.chainofresponsibilities;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/*
@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter { //deprecated from Spring Security 5.7 and removed in 6.0

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests().anyRequest().permitAll().and();
    }

}
*/

/*
Spring Security의 필터는 “순수 Java Web Filter”가 아니라서,
DelegatingFilterProxy + FilterChainProxy 구조를 통해 호출됩니다.

Tomcat
 → DelegatingFilterProxy (Servlet Filter)
   → FilterChainProxy (Spring Bean)
     → ProxyFilterChain
       → Security Filters…
         → DispatcherServlet

왜 사용하지 않을까?
필터를 Spring Bean으로 관리
DI, AOP, 라이프사이클, 설정 조합 가능
환경별 / 요청별 동적 필터 체인 구성

++) DispatcherServlet은 Spring이 만든 Servlet이고, Tomcat은 그 Servlet을 실행해주는 컨테이너일 뿐이다.

 */
@Configuration
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}