
package com.unocode.designpattern;

import com.unocode.factorymethod.FactoryMethodConfig;
import com.unocode.singleton.Settings;
import com.unocode.singleton.SingletonConfig;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.stream.Stream;

@SpringBootApplication
public class DesignPatternApplication {

    public static void main(String[] args) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException, IOException, ClassNotFoundException {
        //싱글톤 테스트
        Settings settings = Settings.getInstance();

        //breaking singleton 1
        Constructor<Settings> constructor = Settings.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Settings settings1 = constructor.newInstance();

        System.out.println(settings == settings1);

        //breaking singleton 2 - serializing, required Settings class must implements Serializable
        Settings settings2 = null;
        try(ObjectOutput out = new ObjectOutputStream(new FileOutputStream("settings.obj"))) {
            out.writeObject(settings);
        }
        try(ObjectInput in = new ObjectInputStream(new FileInputStream("settings.obj"))) {
            settings2 = (Settings) in.readObject();
        }
        System.out.println(settings == settings2); //true if Settings class has readResolve serial method

        //Enum으로 싱글톤을 구현하면 위 두가지 방법에도 안전하게 구현 가능
        //실제 java Runtime 싱글톤으로 구현
        Runtime runtime = Runtime.getRuntime();
        //싱글톤스코프, 엄밀히 말하자면 싱글톤패턴은 아님, 싱글톤패턴은 전체 JVM에서 객체 하나를 보장, 만약 스프링 컨테이너가 2개라면 싱글톤스코프라도 2개의 객체
        /*
        컨테이너 시작
         └─ hello Bean 생성 (1회)
             └─ Singleton Bean Cache 저장
                 └─ getBean("hello") → 캐시에서 반환
                 └─ getBean("hello") → 같은 참조 반환
         */
        ApplicationContext ac = new AnnotationConfigApplicationContext(SingletonConfig.class);
        String hello1 = ac.getBean("hello", String.class);
        String hello2 = ac.getBean("hello", String.class);
        System.out.println(hello1 == hello2);

        //팩토리 메서드 패턴
        BeanFactory javaFactory = new AnnotationConfigApplicationContext(FactoryMethodConfig.class);
        String hello3 = javaFactory.getBean("hello", String.class);

        BeanFactory xmlFactory = new ClassPathXmlApplicationContext("factoryMethodConfig.xml");
        String hello4 = xmlFactory.getBean("hello", String.class);

        //빌더 패턴
        StringBuilder sb = new StringBuilder(); //not synchronized
        String sbString = sb.append("test").append("finished").toString();

        Stream.Builder<String> streamStringBuilder = Stream.builder();
        Stream<String> streamString = streamStringBuilder.add("test").add("finish").build();
        Stream<String> streamString2 = Stream.<String>builder().add("test").add("finish").build();
        streamString.forEach(System.out::println);


        SpringApplication.run(DesignPatternApplication.class, args);
    }

}


