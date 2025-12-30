package com.unocode.structural.bridge;

public class Client {

    /*
    브릿지 패턴 : 추상적인 것과 구체적인 것을 분리 후 연결하는 패턴
    “기능 계층과 구현 계층을 분리해 클래스 폭발을 막고 확장성을 확보하는 구조 패턴”
    "추상화 계층과 구현 계층이 독립적으로 진화해야 브릿지 패턴"

    추상적인 인터페이스에서 구체적인 것을 바로 연결하는 순간 속성이 추가될 경우, 소스 변경양이 급격히 증가함.
    소스 상 DefaultChampion이라는 refined 추상 객체를 생성하고, 합성을 통해 'Skin'이라는 속성을 추가하여, 구체적인 것과 연결.

     */
    public static void main(String[] args) {
        Champion kda아리 = new 아리(new KDA());
        kda아리.skillQ();
        kda아리.skillW();

        Champion poolParty아리 = new 아리(new PoolParty());
        poolParty아리.skillR();
        poolParty아리.skillW();
    }
}
