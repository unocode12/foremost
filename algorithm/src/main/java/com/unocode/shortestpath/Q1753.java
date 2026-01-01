package com.unocode.shortestpath;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.StringTokenizer;

public class Q1753 {

  /*
    access modifier 정리
    | 선언                           | 접근 가능 범위    |
    | ----------------------------- | --------------- |
    | `public`                      | 어디서나          |
    | `protected`                   | 같은 패키지 + 상속 |
    | **default (package-private)** | **같은 패키지**   |
    | `private`                     | 같은 클래스 내부   |
   */

    private static class Node {
        int index;
        int cost;

        Node (int index, int cost) {
            this.index = index;
            this.cost = cost;
        }
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine(), " ");
        int nodeNumber = Integer.parseInt(st.nextToken());
        int hintNumber = Integer.parseInt(st.nextToken());
        int startNode = Integer.parseInt(br.readLine());

        ArrayList<ArrayList<Node>> graph = new ArrayList<>();
        for (int i = 0 ; i <= nodeNumber ; i++) {
            graph.add(new ArrayList<>());
        }

        for (int j = 0 ; j < hintNumber ; j++) {
            st = new StringTokenizer(br.readLine(), " ");
            int firstNode = Integer.parseInt(st.nextToken());
            int secondNode = Integer.parseInt(st.nextToken());
            int cost = Integer.parseInt(st.nextToken());

            graph.get(firstNode).add(new Node(secondNode, cost));
        }

        int[] nodeCost = new int[nodeNumber+1];
        Arrays.fill(nodeCost, Integer.MAX_VALUE);
        nodeCost[startNode] = 0;

        PriorityQueue<Node> priorityQueue = new PriorityQueue<>(Comparator.comparingInt(x -> x.cost));
        priorityQueue.add(new Node(startNode, 0));

        while (!priorityQueue.isEmpty()) {
            Node curr = priorityQueue.poll();

            if (nodeCost[curr.index] < curr.cost) continue;

            for (Node nextNode : graph.get(curr.index)) {
                if (nodeCost[nextNode.index] > curr.cost + nextNode.cost) {
                    nodeCost[nextNode.index] = curr.cost + nextNode.cost;
                    priorityQueue.offer(new Node(nextNode.index, nodeCost[nextNode.index]));
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int k = 1 ; k <= nodeNumber ; k++) {
            if (nodeCost[k] == Integer.MAX_VALUE) {
                sb.append("INF").append("\n");
            } else {
                sb.append(nodeCost[k]).append("\n");
            }
        }
        System.out.println(sb);
    }
}
