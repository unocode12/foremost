package com.unocode.topologicalsorting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.StringTokenizer;

public class Q3665 {

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int resultNumber = Integer.parseInt(br.readLine());

        outer: for (int i = 0 ; i < resultNumber ; i++) {
            int nodeNumber = Integer.parseInt(br.readLine());
            StringTokenizer st = new StringTokenizer(br.readLine(), " ");
            int[] lastRank = new int[nodeNumber];
            boolean[][] graph = new boolean[nodeNumber + 1][nodeNumber + 1];
            int[] indegree = new int[nodeNumber + 1];

            for (int j = 0 ; j < nodeNumber ; j++) {
                lastRank[j] = Integer.parseInt(st.nextToken());
            }

            for (int k = 0 ; k < nodeNumber ; k++) {
                for (int l = k + 1 ; l < nodeNumber ; l++) {
                    int higherRank = lastRank[k];
                    int lowerRank = lastRank[l];
                    graph[higherRank][lowerRank] = true;
                    indegree[lowerRank]++;
                }
            }

            int changeRankNumber = Integer.parseInt(br.readLine());
            for (int m = 0 ; m < changeRankNumber ; m++) {
                st = new StringTokenizer(br.readLine(), " ");
                int firstTarget = Integer.parseInt(st.nextToken());
                int secondTarget = Integer.parseInt(st.nextToken());

                if (graph[firstTarget][secondTarget]) {
                    graph[firstTarget][secondTarget] = false;
                    graph[secondTarget][firstTarget] = true;
                    indegree[secondTarget]--;
                    indegree[firstTarget]++;
                } else {
                    graph[secondTarget][firstTarget] = false;
                    graph[firstTarget][secondTarget] = true;
                    indegree[firstTarget]--;
                    indegree[secondTarget]++;
                }
            }

            Queue<Integer> queue = new ArrayDeque<>();
            for (int n = 1 ; n <= nodeNumber ; n++) {
                if (indegree[n] == 0) queue.offer(n);
            }

            List<Integer> changedRank = new ArrayList<>();
            boolean ambiguous = false;

            for (int o = 0 ; o < nodeNumber ; o++) {
                if (queue.isEmpty()) {
                    System.out.println("IMPOSSIBLE");
                    continue outer;
                }

                if (queue.size() > 1) {
                    ambiguous = true;
                }

                int currentNode = queue.poll();
                changedRank.add(currentNode);

                for (int p = 1 ; p <= nodeNumber ; p++) {
                    if (graph[currentNode][p]) {
                        graph[currentNode][p] = false;
                        indegree[p]--;
                        if (indegree[p] == 0) {
                            queue.offer(p);
                        }
                    }
                }
            }

            if (ambiguous) {
                System.out.println("?");
            } else {
                StringBuilder sb = new StringBuilder();
                for (int v : changedRank) {
                    sb.append(v).append(" ");
                }
                System.out.println(sb.toString().trim());
            }
        }
    }
}
