package com.unocode.structural.decorator;

import org.springframework.beans.factory.xml.BeanDefinitionDecorator;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;

public class DecoratorInSpring {

    public static void main(String[] args) {
        // 빈 설정 데코레이터
        BeanDefinitionDecorator decorator;

        /*
        import org.springframework.core.annotation.Order;
        import org.springframework.http.server.reactive.ServerHttpRequest;
        import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
        import org.springframework.http.server.reactive.ServerHttpResponse;
        import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
        import org.springframework.stereotype.Component;
        import org.springframework.web.server.ServerWebExchange;
        import org.springframework.web.server.WebFilter;
        import org.springframework.web.server.WebFilterChain;
        import reactor.core.publisher.Mono;

        @Component
        @Order(1)
        public class RequestResponseDecoratorFilter implements WebFilter {
            
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
                ServerHttpRequest request = exchange.getRequest();
                ServerHttpResponse response = exchange.getResponse();
                
                // 요청 데코레이터
                ServerHttpRequestDecorator requestDecorator = new ServerHttpRequestDecorator(request) {
                    @Override
                    public String getPathValue(String key) {
                        // 경로 변수 수정
                        return super.getPathValue(key);
                    }
                    
                    @Override
                    public java.util.List<String> getHeaders(String headerName) {
                        // 헤더 값 수정
                        return super.getHeaders(headerName);
                    }
                };
                
                // 응답 데코레이터
                ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(response) {
                    // 응답 헤더나 상태 코드 수정 가능
                };
                
                ServerWebExchange decoratedExchange = exchange.mutate()
                        .request(requestDecorator)
                        .response(responseDecorator)
                        .build();
                
                return chain.filter(decoratedExchange);
            }
        }
        */

        // 웹플럭스 HTTP 요청 /응답 데코레이터
        ServerHttpRequestDecorator httpRequestDecorator;
        ServerHttpResponseDecorator httpResponseDecorator;
    }
}
