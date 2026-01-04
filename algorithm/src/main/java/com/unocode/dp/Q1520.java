package com.unocode.dp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

//https://www.acmicpc.net/problem/1520
public class Q1520 {

    static int rowNumber;
    static int colNumber;
    static int[][] map;
    static int[][] dfsResult;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer st = new StringTokenizer(br.readLine(), " ");

        rowNumber = Integer.parseInt(st.nextToken());
        colNumber = Integer.parseInt(st.nextToken());

        map = new int[rowNumber][colNumber];
        dfsResult = new int[rowNumber][colNumber];
        for (int i = 0 ; i < rowNumber ; i++) {
            st = new StringTokenizer(br.readLine(), " ");
            for (int j = 0 ; j < colNumber ; j++) {
                map[i][j] = Integer.parseInt(st.nextToken());
                dfsResult[i][j] = -1;
            }
        }
        dfsResult[rowNumber-1][colNumber-1] = 1;
        System.out.println(dfs(0,0));
    }

    static int dfs(int row, int col) {
        if (dfsResult[row][col] > -1) return dfsResult[row][col];

        // (-1, 0) (1, 0) (0, -1) (0, 1)
        int result = 0;
        if (row - 1 >= 0 && map[row][col] > map[row-1][col]) {
            result += dfs(row - 1, col);
        }
        if (row + 1 < rowNumber && map[row][col] > map[row+1][col]) {
            result += dfs(row + 1, col);
        }
        if (col - 1 >= 0 && map[row][col] > map[row][col -1]){
            result += dfs(row, col - 1);
        }
        if (col + 1 < colNumber && map[row][col] > map[row][col+1]) {
            result += dfs(row, col + 1);
        }
        return dfsResult[row][col] = result;
    }
}
