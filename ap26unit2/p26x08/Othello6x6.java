package p26x08;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class Othello6x6 {
    private static final int EMPTY = 0;
    private static final int BLACK = 1;
    private static final int WHITE = -1;
    private static final int SIZE = 6;

    private static int[][] board = new int[SIZE][SIZE];
    private static int currentPlayer = BLACK;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("使用法: java Othello6x6 <棋譜文字列>");
            System.out.println("例: java Othello6x6 d2c2b3e4e3f2f4b2b4c5d5a5a1a3b1c1a2d6e5b5f3f5f6e6f1e2a6e1c6b6");
            return;
        }

        String kifu = args[0];
        if (kifu.length() % 2 != 0) {
            System.out.println("エラー: 棋譜の長さが不正です。");
            return;
        }

        initBoard();

        String outputFile = "othello_output.txt";
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(outputFile)))) {
            out.println("=== 初期局面 ===");
            printBoard(out);
            
            for (int i = 0; i < kifu.length() / 2; i++) {
                char c = kifu.charAt(i * 2);
                char r = kifu.charAt(i * 2 + 1);
                
                // パス（".."）の検出と処理
                if (c == '.' && r == '.') {
                    out.println("=== " + (i + 1) + "手目: " + getPlayerName(currentPlayer) + " パス ===");
                    printBoard(out);
                    
                    // 手番を交代して次の手へ
                    currentPlayer = -currentPlayer;
                    continue;
                }
                
                c = Character.toLowerCase(c);
                
                // 座標の範囲チェック
                if (c < 'a' || c > 'f' || r < '1' || r > '6') {
                    System.out.println("エラー: 不正な座標が含まれています (" + c + r + ")");
                    return;
                }
                
                int col = c - 'a';
                int row = r - '1';
                
                // 構文解析した手が合法かチェック
                if (!isValidMove(col, row, currentPlayer)) {
                    out.println("エラー: " + getPlayerName(currentPlayer) + "にとって無効な手です (" + c + r + ")");
                    System.out.println("エラー: 無効な手が検出されたため処理を中断します (" + c + r + ")");
                    return;
                }

                // 石を置き、相手の石を裏返す
                makeMove(col, row, currentPlayer);
                
                out.println("=== " + (i + 1) + "手目: " + getPlayerName(currentPlayer) + " " + c + r + " ===");
                printBoard(out);
                
                // 手番の交代
                currentPlayer = -currentPlayer;
            }
            System.out.println("棋譜の出力を完了しました: " + outputFile);
        } catch (IOException e) {
            System.out.println("ファイル書き込み中にエラーが発生しました: " + e.getMessage());
        }
    }

    // 初期配置のセット
    private static void initBoard() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                board[i][j] = EMPTY;
            }
        }
        // 6x6の初期配置（平行配置）
        board[2][2] = BLACK; // c3
        board[2][3] = WHITE; // d3
        board[3][2] = WHITE; // c4
        board[3][3] = BLACK; // d4
    }

    private static boolean inBounds(int col, int row) {
        return col >= 0 && col < SIZE && row >= 0 && row < SIZE;
    }

    // 指定された座標に石を置けるか判定
    private static boolean isValidMove(int col, int row, int player) {
        if (!inBounds(col, row) || board[row][col] != EMPTY) return false;

        int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dy = {-1, -1, -1, 0, 0, 1, 1, 1};

        for (int i = 0; i < 8; i++) {
            int x = col + dx[i];
            int y = row + dy[i];
            int count = 0;

            while (inBounds(x, y) && board[y][x] == -player) {
                count++;
                x += dx[i];
                y += dy[i];
            }

            if (count > 0 && inBounds(x, y) && board[y][x] == player) {
                return true;
            }
        }
        return false;
    }

    // 石を置き、挟んだ石を裏返す
    private static void makeMove(int col, int row, int player) {
        board[row][col] = player;
        int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dy = {-1, -1, -1, 0, 0, 1, 1, 1};

        for (int i = 0; i < 8; i++) {
            int x = col + dx[i];
            int y = row + dy[i];
            int count = 0;

            while (inBounds(x, y) && board[y][x] == -player) {
                count++;
                x += dx[i];
                y += dy[i];
            }

            if (count > 0 && inBounds(x, y) && board[y][x] == player) {
                int flipX = col + dx[i];
                int flipY = row + dy[i];
                while (flipX != x || flipY != y) {
                    board[flipY][flipX] = player;
                    flipX += dx[i];
                    flipY += dy[i];
                }
            }
        }
    }

    // 盤面をファイルに出力
    private static void printBoard(PrintWriter out) {
        out.println("  a b c d e f");
        for (int r = 0; r < SIZE; r++) {
            out.print((r + 1) + " ");
            for (int c = 0; c < SIZE; c++) {
                if (board[r][c] == BLACK) {
                    out.print("● ");
                } else if (board[r][c] == WHITE) {
                    out.print("○ ");
                } else {
                    out.print(". ");
                }
            }
            out.println();
        }
        out.println();
    }
    
    private static String getPlayerName(int player) {
        return player == BLACK ? "黒" : "白";
    }
}