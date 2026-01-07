package com.unocode.treedp;

import java.io.*;
import java.util.*;

public class Q1949 {

    static int[] population;
    static int[][] dp;
    static List<List<Integer>> graph = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int n = Integer.parseInt(br.readLine());

        population = new int[n + 1];
        dp = new int[n + 1][2];

        StringTokenizer st = new StringTokenizer(br.readLine());
        for (int i = 1; i <= n; i++) {
            population[i] = Integer.parseInt(st.nextToken());
        }

        for (int i = 0; i <= n; i++) {
            graph.add(new ArrayList<>());
        }

        for (int i = 0; i < n - 1; i++) {
            st = new StringTokenizer(br.readLine());
            int a = Integer.parseInt(st.nextToken());
            int b = Integer.parseInt(st.nextToken());
            graph.get(a).add(b);
            graph.get(b).add(a);
        }

        dfs(1, 0);

        System.out.println(Math.max(dp[1][0], dp[1][1]));
    }

    static void dfs(int node, int parentNode) {
        dp[node][1] = population[node];

        for (int next : graph.get(node)) {
            if (next == parentNode) continue;

            dfs(next, node);
            dp[node][1] += dp[next][0];
            dp[node][0] += Math.max(dp[next][0], dp[next][1]);
        }
    }

}
