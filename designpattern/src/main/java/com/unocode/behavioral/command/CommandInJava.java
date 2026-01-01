package com.unocode.behavioral.command;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CommandInJava {

    public static void main(String[] args) {
        Light light = new Light();
        Game game = new Game();
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        /*
        Runnable을 submit이 파라미터로 받는데, 바로 이 Runnable이 커맨드 패턴
        밑에서는 메소드 레퍼런스로 할당해서 light 혹은 game에 직접 접근하는 것처럼 보일지 몰라도,
        사실 Runnable 익명객체로 명시하여 해당 커맨드를 넘기는것임.
         */
        executorService.submit(light::on);
        executorService.submit(game::start);
        executorService.submit(game::end);
        executorService.submit(light::off);
        executorService.shutdown();
    }
}
