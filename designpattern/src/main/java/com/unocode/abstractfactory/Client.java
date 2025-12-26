package com.unocode.abstractfactory;

import com.unocode.factorymethod.Ship;
import com.unocode.factorymethod.ShipFactory;

public class Client {

    //“서로 관련 있는 객체들을 한 세트로 만들고 싶다”
    //"구체적으로 어떤 클래스를 사용하는지 감출 수 있다."

    /*
    제품군(Product Family) 생성
    관련 객체의 일관성 보장
    확장은 새 팩토리 구현체 추가

    example.
    같은 테마의 객체 세트를 교체해야 할 때(DarkTheme / LightTheme)
    플랫폼 별 UI, DB 별 드라이버 묶음 등

    구분	        팩토리 메서드	            추상 팩토리
    해결 문제	객체 하나 생성 책임 분리	객체 묶음 생성
    생성 결정	상속(Override)	        구성(주입)
    초점	        “누가 만들까?”	        “어떤 세트로 만들까?”
    확장 방식	서브클래스 추가	        팩토리 구현체 추가
    의존성	    Creator ↔ Product	    Client ↔ Factory

    스프링에서는 FactoryBean이 팩토리 메서드 패턴이고, BeanFactory/ApplicationContext가 환경 별 빈 세트를 제공하는 추상 팩토리 역할을 합니다.

    */

    public static void main(String[] args) {
        ShipFactory shipFactory = new WhiteshipFactory(new WhiteshipPartsFactory());
        Ship ship = shipFactory.createShip();
        System.out.println(ship.getAnchor().getClass());
        System.out.println(ship.getWheel().getClass());
    }
}
