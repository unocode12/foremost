package com.unocode.structural.decorator;

import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DecoratorInJava {

    public static void main(String[] args) {
        // Collections가 제공하는 데코레이터 메소드
        ArrayList list = new ArrayList<>();
        list.add(new Book());

        List books = Collections.checkedList(list, Book.class);
        list.add(new Item());
        books.add(new Item()); // 컴파일 오류 발생

        List unmodifiableList = Collections.unmodifiableList(list);
        list.add(new Item()); 
        unmodifiableList.add(new Book()); // 컴파일 오류 발생


        // 서블릿 요청 또는 응답 랩퍼

        /*
        import jakarta.servlet.*;
        import jakarta.servlet.http.HttpServletRequest;
        import jakarta.servlet.http.HttpServletRequestWrapper;
        import java.io.IOException;

        public class RequestDecoratorFilter implements Filter {
            
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, 
                                FilterChain chain) throws IOException, ServletException {
                
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                
                // 요청을 래핑하여 기능 추가
                HttpServletRequestWrapper requestWrapper = new HttpServletRequestWrapper(httpRequest) {
                    @Override
                    public String getParameter(String name) {
                        String value = super.getParameter(name);
                        // 데코레이터 패턴: 기존 기능에 추가 기능
                        return value != null ? value.trim() : null;
                    }
                    
                    @Override
                    public String getHeader(String name) {
                        String header = super.getHeader(name);
                        // 헤더 값 변환 로직 추가
                        return header;
                    }
                };
                
                // 래핑된 요청을 다음 필터/서블릿으로 전달
                chain.doFilter(requestWrapper, response);
            }
        }

        import org.springframework.boot.web.servlet.FilterRegistrationBean;
        import org.springframework.context.annotation.Bean;
        import org.springframework.context.annotation.Configuration;

        @Configuration
        public class FilterConfig {
            
            @Bean
            public FilterRegistrationBean<RequestDecoratorFilter> requestDecoratorFilter() {
                FilterRegistrationBean<RequestDecoratorFilter> registrationBean = new FilterRegistrationBean<>();
                registrationBean.setFilter(new RequestDecoratorFilter());
                registrationBean.addUrlPatterns("/*");
                registrationBean.setOrder(1);
                return registrationBean;
            }
        }
        */

        HttpServletRequestWrapper requestWrapper;
        HttpServletResponseWrapper responseWrapper;
    }

    private static class Book {

    }

    private static class Item {

    }
}
