package com.unocode.dp;

import java.io.*;
import java.util.*;

public class Q2098 {

    static int N;
    static int[][] cost;
    static int[][] dp;
    static final int INF = 1_000_000_000;
    static int allVisited;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        N = Integer.parseInt(br.readLine());
        cost = new int[N][N];
        dp = new int[N][1 << N];
        allVisited = (1 << N) - 1;
        StringTokenizer st;

        for (int i = 0 ; i < N ; i++) {
            Arrays.fill(dp[i], -1);
            st = new StringTokenizer(br.readLine());
            for (int j = 0 ; j < N ; j++) {
                cost[i][j] = Integer.parseInt(st.nextToken());
            }
        }

        System.out.println(tsp(0, 1));


    }



    static int tsp(int cur, int visited) {
        if (visited == allVisited) {
            if (cost[cur][0] != 0) return cost[cur][0];
            else return INF;
        }

        if (dp[cur][visited] != -1) return dp[cur][visited];

        int result = INF;

        for (int next = 0 ; next < N ; next++) {
            if ((visited & (1 << next)) != 0) continue;
            if (cost[cur][next] == 0) continue;

            int tmp = cost[cur][next] + tsp(next, visited | (1 << next));
            result = Math.min(result, tmp);
        }

        dp[cur][visited] = result;
        return result;
    }
}
