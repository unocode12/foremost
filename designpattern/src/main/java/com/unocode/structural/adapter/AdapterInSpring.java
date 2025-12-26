package com.unocode.structural.adapter;

import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

public class AdapterInSpring {

    public static void main(String[] args) {
        DispatcherServlet dispatcherServlet = new DispatcherServlet();
  ↓
        /*

        DispatcherServlet.doService()
        모든 요청은 무조건 DispatcherServlet을 먼저 거침
        Front Controller 패턴

        protected void doDispatch(HttpServletRequest request, HttpServletResponse response)
        이 메서드가 Spring MVC 요청 처리의 90%

        (1) Multipart 체크
        processedRequest = checkMultipart(request);
        파일 업로드인지 확인
        MultipartResolver가 있으면 MultipartHttpServletRequest로 변환

        (2) Handler 찾기 (Controller 매핑)
        mappedHandler = getHandler(processedRequest);
        내부적으로:
            HandlerMapping 리스트 순회
                대표 구현체:
                    RequestMappingHandlerMapping
                    BeanNameUrlHandlerMapping
        📌 여기서 @RequestMapping / @GetMapping 이 매칭됨

        (3) 인터셉터 preHandle
        mappedHandler.applyPreHandle(...)
        HandlerInterceptor.preHandle()
        인증, 로깅, 권한 체크

        (4) HandlerAdapter 선택
        HandlerAdapter ha = getHandlerAdapter(handler);
        왜 필요? 컨트롤러 형태가 여러 가지라서
            @Controller
            HttpRequestHandler
            옛날 Controller 인터페이스
        📌 어댑터 패턴

        (5) Controller 실행
        mv = ha.handle(request, response, handler);
        실제로 컨트롤러 메서드 호출

        결과:
            ModelAndView
            또는 @ResponseBody → 바로 응답
            다음 중 하나면 무조건 MessageConverter 경로로 감:
                @ResponseBody
                @RestController
                ResponseEntity<T>

        (6) 인터셉터 postHandle
        mappedHandler.applyPostHandle(...)

        (7) 예외 처리
        processHandlerException(...)
        HandlerExceptionResolver 체인 실행
            @ExceptionHandler
            @ControllerAdvice
            ResponseStatusExceptionResolver

        (8) View 렌더링
        render(mv, request, response);
        ViewResolver 사용
            JSP
            Thymeleaf
            JSON (MappingJackson2JsonView)

        (9) afterCompletion
        mappedHandler.triggerAfterCompletion(...)
        리소스 정리
        트랜잭션 종료 시점

        스프링부트에서는 @SpringBootApplication, spring-boot-starter-web을 통해 DispatcherServletAutoConfiguration

        정리
        [공통]
        Client HTTP Request
                ↓
        DispatcherServlet
                ↓
        HandlerMapping
                ↓
        HandlerAdapter
                ↓
        Controller 메서드 실행

        밑에서 갈림길
        @Controller
        public String view() { ... }

        Controller
          ↓
        ModelAndView 생성
          ↓
        DispatcherServlet.render()
          ↓
        ViewResolver 체인
          ↓
        View.render()
          ↓
        HTML 응답

        @RestController
        public UserDto api() { ... }

        Controller
          ↓
        RequestResponseBodyMethodProcessor
          ↓
        HttpMessageConverter 선택
            MappingJackson2HttpMessageConverter → JSON
            StringHttpMessageConverter → text/plain
            ByteArrayHttpMessageConverter
            FormHttpMessageConverter
          ↓
        write()
          ↓
        JSON 응답
        */

        HandlerAdapter handlerAdapter = new RequestMappingHandlerAdapter();
    }
}
