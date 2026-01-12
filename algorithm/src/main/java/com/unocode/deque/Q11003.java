package com.unocode.deque;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.StringTokenizer;

public class Q11003 {

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine(), " ");

        int N = Integer.parseInt(st.nextToken());
        int L = Integer.parseInt(st.nextToken());

        st = new StringTokenizer(br.readLine(), " ");
        ArrayDeque<int[]> deque = new ArrayDeque<>();
        StringBuilder sb = new StringBuilder();

        for (int i = 0 ; i < N ; i++) {
            int value = Integer.parseInt(st.nextToken());

            while (!deque.isEmpty() && (deque.peekLast()[0] > value)) {
                deque.pollLast();
            }
            deque.offerLast(new int[]{value, i});

            while (!deque.isEmpty() && (deque.peekFirst()[1] <= i - L)) {
                deque.pollFirst();
            }

            if (!deque.isEmpty()) sb.append(deque.peekFirst()[0]).append(' ');
        }

        System.out.println(sb);
    }
}
