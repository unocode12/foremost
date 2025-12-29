package com.unocode.backtracking;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class Q2580 {

    static int[][] sudoku = new int[9][9];
    static List<Point> emptyPoints = new ArrayList<>();
    static boolean finished = false;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        for (int i = 0 ; i < 9 ; i++) {
            StringTokenizer st = new StringTokenizer(br.readLine(), " ");
            for (int j = 0 ; j  < 9 ; j++) {
                int tempNumber = Integer.parseInt(st.nextToken());
                sudoku[i][j] = tempNumber;
                if (tempNumber == 0) {
                    emptyPoints.add(new Point(i, j));
                }
            }
        }

        dfs(0);

    }

    static void dfs(int idx) {
        if (idx == emptyPoints.size()) {
            printSudoku();
            finished = true;
            return;
        }

        Point p = emptyPoints.get(idx);
        int x = p.x;
        int y = p.y;

        boolean[] used = new boolean[10];

        // 행 / 열 / 박스 체크
        for (int i = 0; i < 9; i++) {
            used[sudoku[x][i]] = true;
            used[sudoku[i][y]] = true;
            used[sudoku[(x / 3) * 3 + i / 3][(y / 3) * 3 + i % 3]] = true;
        }

        for (int num = 1; num <= 9; num++) {
            if (!used[num]) {
                sudoku[x][y] = num;
                dfs(idx + 1);
                if (finished) return; //이 부분을 꼭 해줘야 함. 이 로직이 없으면 백트래킹 도중 스스로 스도쿠를 망가트림 + 쓸데없는 추가 로직이 들어감.
                sudoku[x][y] = 0;
            }
        }
    }

    static void printSudoku() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0 ; i < 9 ; i++) {
            for (int j = 0 ; j < 9 ; j++) {
                sb.append(sudoku[i][j]).append(" ");
            }
            sb.append("\n");
        }
        System.out.print(sb);
    }

    static class Point {
        int x;
        int y;

        Point(int x,int y) {
            this.x = x;
            this.y = y;
        }
    }
}
