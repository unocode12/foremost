package com.unocode.tree;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class Q1167 {

    static class Edge {
        int to;
        int weigth;

        Edge(int to, int weigth) {
            this.to = to;
            this.weigth = weigth;
        }
    }

    static List<List<Edge>> tree;
    static boolean[] visited;
    static int maxDist = 0;
    static int farNode = 0;


    public static void main(String[] args) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        int V = Integer.parseInt(br.readLine());
        tree = new ArrayList<>();

        for (int i = 0 ; i <= V ; i++) {
            tree.add(new ArrayList<>());
        }

        for (int i = 1; i <= V; i++) {
            StringTokenizer st = new StringTokenizer(br.readLine());
            int from = Integer.parseInt(st.nextToken());

            while(true) {
                int to = Integer.parseInt(st.nextToken());
                if (to == -1) break;

                int weight = Integer.parseInt(st.nextToken());
                tree.get(from).add(new Edge(to, weight));
            }
        }

        visited = new boolean[V + 1];
        dfs(1, 0);

        visited = new boolean[V + 1];
        maxDist = 0;
        dfs(farNode, 0);

        System.out.println(maxDist);
    }

    static void dfs(int node, int dist) {
        visited[node] = true;

        if (dist > maxDist) {
            maxDist = dist;
            farNode = node;
        }

        for (Edge e : tree.get(node)) {
            if (!visited[e.to]) {
                dfs(e.to, dist + e.weigth);
            }
        }
    }
}
