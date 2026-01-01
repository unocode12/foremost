package com.unocode.topologicalsorting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.StringTokenizer;

public class Q1766 {

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine(), " ");

        int problemNumber = Integer.parseInt(st.nextToken());
        int hintNumber = Integer.parseInt(st.nextToken());

        // boolean[][] graph = new boolean[problemNumber+1][problemNumber+1];
        // 문제 수가 32000이므로 너무 공간 메모리가 높다. 아래와 같이 줄인다.
        // initial capacity를 주는게 더 좋다.
        List<List<Integer>> graph = new ArrayList<>(problemNumber + 1);
        for (int i = 0; i <= problemNumber; i++) {
            graph.add(new ArrayList<>());
        }
        int[] indegree = new int[problemNumber+1];

        for (int i = 0 ; i < hintNumber ; i++) {
            st = new StringTokenizer(br.readLine(), " ");
            int firstProblem = Integer.parseInt(st.nextToken());
            int secondProblem = Integer.parseInt(st.nextToken());

            graph.get(firstProblem).add(secondProblem);
            indegree[secondProblem]++;
        }

        PriorityQueue<Integer> priorityQueue = new PriorityQueue<>();

        for (int j = 1; j <= problemNumber ; j++) {
            if (indegree[j] == 0) priorityQueue.offer(j);
        }

        StringBuilder sb = new StringBuilder();
        while (!priorityQueue.isEmpty()) {
            int curr = priorityQueue.poll();
            sb.append(curr).append(" ");

            for (int problem : graph.get(curr)) {
                indegree[problem]--;
                if (indegree[problem] == 0) priorityQueue.offer(problem);
            }
        }
        System.out.println(sb);
    }
}
