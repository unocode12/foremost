package com.unocode.structural.proxy;

public class Client {
    /*
    프록시패턴 : 특정 객체에 접근을 제어하거나 기능을 추가할 수 있는 패턴
    초기화 지연, 로깅, 캐싱, 접근 제어 등 다양하게 응용하여 사용 가능

    프록시 패턴은 “접근 제어”,
    데코레이터(장식자) 패턴은 “기능 확장”

    둘다 아래와 같은 구조
    Client
      ↓
    Wrapper (Proxy / Decorator)
      ↓
    Real Object

    “실제 객체에 접근하기 전/후에 제어 로직을 넣는다” => 프록시
    “기존 객체의 기능을 조합해서 확장한다” => 데코레이터

     */

    public static void main(String[] args) {
        GameService gameService = new GameServiceProxy();
        gameService.startGame();
    }
}
