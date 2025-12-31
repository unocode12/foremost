package com.unocode.structural.flyweight;

public class Client {
    /*
    플라이웨이트 패턴
    자주 변하는 부분(extrinsic)과 고정되는 부분(intrinsic)한 부분을 구분하고
    고정되는 부분에 대해서는 캐싱과 같은 재사용 전략을 사용하여, 객체 생성 시의 메모리를 줄이는 패턴
     */

    public static void main(String[] args) {
        FontFactory fontFactory = new FontFactory();
        Character c1 = new Character('h', "white", fontFactory.getFont("nanum:12"));
        Character c2 = new Character('e', "white", fontFactory.getFont("nanum:12"));
        Character c3 = new Character('l', "white", fontFactory.getFont("nanum:12"));
    }
}
