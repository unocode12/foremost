package com.unocode.shortestpath;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.StringTokenizer;

public class Q1504 {

    static final int INF = 200_000_000;

    static int[] dijkstra;
    static int[] dijkstraStop1;
    static int[] dijkstraStop2;

    static class Node {
        int nextNode;
        int cost;

        Node(int nextNode, int cost) {
            this.nextNode = nextNode;
            this.cost = cost;
        }
    }

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine(), " ");

        int nodeNumber = Integer.parseInt(st.nextToken());
        int hintNumber = Integer.parseInt(st.nextToken());

        List<List<Node>> graph = new ArrayList<>(nodeNumber+1);

        for (int i = 0 ; i <= nodeNumber ; i++) {
            graph.add(new ArrayList<>());
        }

        for (int j = 0 ; j < hintNumber ; j++) {
            st = new StringTokenizer(br.readLine(), " ");
            int firstNode = Integer.parseInt(st.nextToken());
            int secondNode = Integer.parseInt(st.nextToken());
            int cost = Integer.parseInt(st.nextToken());

            graph.get(firstNode).add(new Node(secondNode, cost));
            graph.get(secondNode).add(new Node(firstNode, cost));
        }

        st = new StringTokenizer(br.readLine(), " ");
        int firstStopNode = Integer.parseInt(st.nextToken());
        int secondStopNode = Integer.parseInt(st.nextToken());

        dijkstra = new int[nodeNumber+1];
        dijkstraStop1 = new int[nodeNumber+1];
        dijkstraStop2 = new int[nodeNumber+1];
        Arrays.fill(dijkstra, INF);
        Arrays.fill(dijkstraStop1, INF);
        Arrays.fill(dijkstraStop2, INF);
        dijkstra(graph, 1, dijkstra);
        dijkstra(graph, firstStopNode, dijkstraStop1);
        dijkstra(graph, secondStopNode, dijkstraStop2);

        //가장 작은 거리는 둘 중 하나
        //dijkstra[firstStopNode] + dijkstraStop1[secondStopNode] + dijkstraStop2[nodeNumber]
        //dijkstra[secondStopNode] + dijkstraStop2[firstStopNode] + dijkstraStop1[nodeNumber]
        long firstCaseCost = dijkstra[firstStopNode] + dijkstraStop1[secondStopNode] + dijkstraStop2[nodeNumber];
        long secondCaseCost = dijkstra[secondStopNode] + dijkstraStop2[firstStopNode] + dijkstraStop1[nodeNumber];

        long answer = Math.min(firstCaseCost, secondCaseCost);

        if (answer >= INF) {
            System.out.println("-1");
        } else {
            System.out.println(answer);
        }

    }

    static void dijkstra(List<List<Node>> graph, int start, int[] dist) {
        Arrays.fill(dist, INF);
        PriorityQueue<Node> pq = new PriorityQueue<>(Comparator.comparingInt(n -> n.cost));
        pq.offer(new Node(start, 0));
        dist[start] = 0;

        while (!pq.isEmpty()) {
            Node curr = pq.poll();

            if (dist[curr.nextNode] < curr.cost) continue;

            for (Node next : graph.get(curr.nextNode)) {
                int newCost = curr.cost + next.cost;
                if (dist[next.nextNode] > newCost) {
                    dist[next.nextNode] = newCost;
                    pq.offer(new Node(next.nextNode, newCost));
                }
            }
        }
    }

}
