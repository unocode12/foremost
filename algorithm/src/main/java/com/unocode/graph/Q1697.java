package com.unocode.graph;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.StringTokenizer;

public class Q1697 {

    static final int MAX = 100000;

    public static void main(String[] args) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine());

        int N = Integer.parseInt(st.nextToken());
        int K = Integer.parseInt(st.nextToken());

        boolean[] visited = new boolean[MAX + 1];
        Queue<Try> queue = new ArrayDeque<>();

        queue.offer(new Try(0, N));
        visited[N] = true;

        while (!queue.isEmpty()) {
            Try cur = queue.poll();

            if (cur.position == K) {
                System.out.println(cur.count);
                return;
            }

            int nextCount = cur.count + 1;
            if (cur.position - 1 >= 0 && !visited[cur.position - 1]) {
                visited[cur.position - 1] = true;
                queue.offer(new Try(nextCount, cur.position - 1));
            }

            if (cur.position + 1 <= MAX && !visited[cur.position + 1]) {
                visited[cur.position + 1] = true;
                queue.offer(new Try(nextCount, cur.position + 1));
            }

            if (cur.position * 2 <= MAX && !visited[cur.position * 2]) {
                visited[cur.position * 2] = true;
                queue.offer(new Try(nextCount,cur.position * 2));
            }
        }
    }

    static class Try {
        int count;
        int position;

        Try(int count, int position) {
            this.count = count;
            this.position = position;
        }
    }
}
