package com.unocode.bruteforce;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

//백준 2798 - 부르트포스
/*
이 문제는 정렬을 하고 푸는 것보다는 문제 조건을 확인하였을 때 삼중 for문으로 문제를 해결하는 것이 좋다
문제 조건
    카드 개수 N ≤ 100
    3장을 뽑는 모든 경우 탐색
    목표 합 M ≤ 300,000

조합으로 볼 때, 약 16만번의 연산이 필요하며 자바 기준 충분하게 빠르다.
또한, 정렬이 의미가 없는게 결국 모든 경우의 수를 비교하여야 하므로, 오히려 시간 복잡도만 증가할 뿐이다.

참고지식
자바 어레이 특징(아래에서 int[])
1. 연속 메모리 할당
2. 크기 고정
3. 인덱스 기반 접근
4. O(1)의 조회
5. O(N)의 삽입 삭제
6. 연속 메모리라 캐시 친화적

ArrayList 특징
1. 내부는 어레이와 동일
2. 동적 크기 조절
3, capacity 관리
4. O(1)의 조회
5. O(N)의 삽입 삭제 (내부 배열 복사 발생, System.arraycopy(...))
6. 연속 메모리라 캐시 친화적

transient Object[] elementData;
private int size;
size: 실제 원소 수
capacity: 배열 길이

new ArrayList<>();
capacity = 0
첫 add 시 기본 capacity = 10
capacity 증가할 수록 newCapacity = oldCapacity + (oldCapacity >> 1);를 통해 약 1.5배로 증가

amortized O(1)은 일부 연산이 O(n)이더라도
전체 연산 비용을 나누면 한 번당 O(1)이 되는 것을 의미합니다.
ArrayList의 add는 resize 비용을 포함해도 전체적으로 O(1)로 보장됩니다.


LinkedList 특징
1. 이중 연결 리스트
2. 노드 기반
class Node<E> {
    E item;
    Node<E> next;
    Node<E> prev;
}
3. 메모리 비연속
4. O(N)의 조회
5. O(1)의 삽입 삭제일 것 같지만, 결국 탐색 비용이 추가되기 때문에 O(N)
6. 연속 메모리가 아닌 포인터 채이싱 방식이기 때문에 캐시 친화적이지 않음
그럼 이거 언제 사용?
1, 대신 맨 앞 혹은 맨 뒤에서의 접근은 빠르기 때문에 앞,뒤 큐 구조에 사용
2. iterator 기반 순차 접근 시
3. Deque 구조
=> 하지만 큐나, 디큐 사용 시 링크드리스트 대신 ArrayDeque 사용

ArrayDeque
[ ][ ][A][B][C][ ][ ]
      ↑ head    ↑ tail
원형 배열
head / tail 포인터
연속 메모리

LinkedList
[A] <-> [B] <-> [C]
이중 연결 리스트
노드 기반

*/

public class Q2798 {

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine(), " ");

        int numberOfCards = Integer.parseInt(st.nextToken());
        int targetOfSum = Integer.parseInt(st.nextToken());

        int[] cards = new int[numberOfCards];

        st = new StringTokenizer(br.readLine(), " ");
        for (int i = 0 ; i < numberOfCards ; i++) {
            cards[i] = Integer.parseInt(st.nextToken());
        }

        int result = search(numberOfCards, targetOfSum, cards);
        System.out.println(result);
    }

    private static int search(int numberOfCards, int targetOfSum, int[] cards) {
        int result = 0;

        for (int i = 0 ; i < numberOfCards - 2 ; i++){
            if (cards[i] > targetOfSum) continue;
            for (int j = i + 1 ; j < numberOfCards - 1 ; j++) {
                if(cards[i] + cards[j] > targetOfSum) continue;
                for (int k = j + 1 ; k < numberOfCards ; k++) {
                    int tempResult = cards[i] + cards[j] + cards[k];
                    if (tempResult == targetOfSum) return tempResult;
                    if (tempResult > result && tempResult < targetOfSum) result = tempResult;
                }
            }
        }
        return result;
    }
}
