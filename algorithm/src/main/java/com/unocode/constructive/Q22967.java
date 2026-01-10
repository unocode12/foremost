package com.unocode.constructive;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class Q22967 {

    static class Connection {
        int u, v;
        Connection(int u, int v) {
            this.u = u;
            this.v = v;
        }
    }

    static List<List<Integer>> graph;
    static List<Connection> addedEdges;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int N = Integer.parseInt(br.readLine());

        graph = new ArrayList<>();
        addedEdges = new ArrayList<>();

        for (int i = 0; i <= N; i++) {
            graph.add(new ArrayList<>());
        }

        for (int i = 0; i < N - 1; i++) {
            StringTokenizer st = new StringTokenizer(br.readLine());
            int u = Integer.parseInt(st.nextToken());
            int v = Integer.parseInt(st.nextToken());
            graph.get(u).add(v);
            graph.get(v).add(u);
        }

        if (N <= 4) {
            for (int i = 1; i <= N; i++) {
                for (int j = i + 1; j <= N; j++) {
                    if (!graph.get(i).contains(j)) {
                        addedEdges.add(new Connection(i, j));
                    }
                }
            }

            System.out.println(addedEdges.size());
            System.out.println(1);
            for (Connection c : addedEdges) {
                System.out.println(c.u + " " + c.v);
            }
            return;
        }

        int center = 1;
        for (int i = 2; i <= N; i++) {
            if (!graph.get(center).contains(i)) {
                addedEdges.add(new Connection(center, i));
            }
        }

        System.out.println(addedEdges.size());
        System.out.println(2);
        for (Connection c : addedEdges) {
            System.out.println(c.u + " " + c.v);
        }
    }
}
