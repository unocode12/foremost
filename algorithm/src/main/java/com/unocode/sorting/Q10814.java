package com.unocode.sorting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

/*
자료구조	    내부 구조
HashMap	    해시 테이블 + 버킷
TreeMap	    Red-Black Tree (균형 이진 트리)

연산	        HashMap	    TreeMap
put	        O(1) (평균)	O(log N)
get	        O(1) (평균)	O(log N)
remove	    O(1)	    O(log N)
순회	        무작위	    정렬된 순서
*/

public class Q10814 {

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        int numberOfPeople = Integer.parseInt(st.nextToken());
        Map<Integer, List<String>> listOfPeople = new TreeMap<>(); //역순을 원할 때에는, new TreeMap<>(Collections.reverseOrder());
        for (int i = 0 ; i < numberOfPeople ; i++) {
            StringTokenizer tempSt = new StringTokenizer(br.readLine(), " ");
            int age = Integer.parseInt(tempSt.nextToken());
            String name = tempSt.nextToken();

            listOfPeople
                    .computeIfAbsent(age, k -> new ArrayList<>())
                    .add(name);
        }
        StringBuilder sb = new StringBuilder();
        for(Map.Entry<Integer, List<String>> map : listOfPeople.entrySet()) {
            for (int i = 0 ; i < map.getValue().size() ; i++) {
                sb.append(map.getKey()).append(" ").append(map.getValue().get(i)).append("\n");
            }
        }
        System.out.print(sb);



        /*
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int n = Integer.parseInt(br.readLine());

        List<Person> list = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            StringTokenizer st = new StringTokenizer(br.readLine());
            int age = Integer.parseInt(st.nextToken());
            String name = st.nextToken();
            list.add(new Person(age, name));
        }

        // 나이 기준 오름차순 정렬 (stable)
        list.sort(Comparator.comparingInt(p -> p.age));

        StringBuilder sb = new StringBuilder();
        for (Person p : list) {
            sb.append(p.age).append(" ").append(p.name).append("\n");
        }

        System.out.print(sb);
        */

    }

    static class Person {
        int age;
        String name;

        Person(int age, String name) {
            this.age = age;
            this.name = name;
        }
    }
}
