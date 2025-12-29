package com.unocode.backtracking;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class Q2580_2 {

    static int[][] sudoku = new int[9][9];
    static List<Point> blanks = new ArrayList<>();

    // 이 부분이 속도를 높이는데 일조함. 처음 세팅 시 이 부분을 미리 세팅함.
    // 추가적으로 box를 하나의 row로 인식하기 위해서 boxIndex를 고려하는 점이 아이디어!
    static boolean[][] row = new boolean[9][10];
    static boolean[][] col = new boolean[9][10];
    static boolean[][] box = new boolean[9][10];

    static boolean solved = false;

    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        for (int i = 0; i < 9; i++) {
            StringTokenizer st = new StringTokenizer(br.readLine());
            for (int j = 0; j < 9; j++) {
                int v = Integer.parseInt(st.nextToken());
                sudoku[i][j] = v;

                if (v == 0) {
                    blanks.add(new Point(i, j));
                } else {
                    row[i][v] = true;
                    col[j][v] = true;
                    box[boxIndex(i, j)][v] = true;
                }
            }
        }

        dfs(0);
    }

    static void dfs(int depth) {
        if (depth == blanks.size()) {
            print();
            solved = true;
            return;
        }

        Point p = blanks.get(depth);
        int x = p.x;
        int y = p.y;
        int b = boxIndex(x, y);

        for (int num = 1; num <= 9; num++) {
            if (row[x][num] || col[y][num] || box[b][num]) continue;

            sudoku[x][y] = num;
            row[x][num] = col[y][num] = box[b][num] = true;

            dfs(depth + 1);
            if (solved) return; //마찬가지로, 한번 풀리면 추가 로직은 수행하지 않는다.

            sudoku[x][y] = 0;
            row[x][num] = col[y][num] = box[b][num] = false;
        }
    }

    static int boxIndex(int x, int y) {
        return (x / 3) * 3 + (y / 3);
    }

    static void print() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                sb.append(sudoku[i][j]).append(" ");
            }
            sb.append("\n");
        }
        System.out.print(sb);
    }

    static class Point {
        int x, y;
        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
