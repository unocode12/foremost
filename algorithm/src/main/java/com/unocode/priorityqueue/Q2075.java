package com.unocode.priorityqueue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.PriorityQueue;
import java.util.StringTokenizer;

public class Q2075 {
    /*
    큐 사용 시 실무에서는 offer 사용 권장, add 시 기존 큐에서는 exception 발생, 우선순위 큐에서는 add 해도 offer와 같음.
    큐에서는 offer, poll, peek 사용 권장.
    리스트 혹은 셋에서는 add 사용.
     */
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int N = Integer.parseInt(br.readLine());
        PriorityQueue<Integer> priorityQueue = new PriorityQueue<>(); //오름차순 정렬 큐, 작은 수가 peek, poll 대상.
        for (int i = 0 ; i < N ; i++) {
            StringTokenizer st = new StringTokenizer(br.readLine(), " ");
            for (int j = 0 ; j < N ; j++) {
                int number = Integer.parseInt(st.nextToken());
                if (priorityQueue.size() < N) {
                    priorityQueue.offer(number);
                } else if (number > priorityQueue.peek()) {
                    priorityQueue.poll();
                    priorityQueue.offer(number);
                }
            }
        }

        System.out.println(priorityQueue.peek());
    }
}
