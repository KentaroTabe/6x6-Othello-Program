package p26x08;

import ap26.Board;
import ap26.Color;
import ap26.Player;
import myplayer.MyBoard;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class EvalTester {

    // ログファイル出力先
    private static final String LOG_FILE = "../ap26unit1/outputs/log.txt";
    
    // N順 (計 2N 対局) 実施する設定
    private static final int N = 5; 

    public static void main(String[] args) {
        System.out.println("=== 新旧 評価関数 対決 (" + N + "順 / 計 " + (2 * N) + " 対局) ===");
        System.out.println("・旧AI: OurPlayer (マスの重みのみ)");
        System.out.println("・新AI: OurPlayer2 (マスの重み ＋ モビリティ評価)");
        System.out.println();

        // 獲得ポイント（勝ち点）の集計用変数
        int oldAsBlackPoints = 0;
        int oldAsWhitePoints = 0;
        int newAsBlackPoints = 0;
        int newAsWhitePoints = 0;

        try {
            Files.createDirectories(Paths.get(LOG_FILE).getParent());
        } catch (IOException e) {
            System.err.println("ログディレクトリの作成に失敗しました: " + e.getMessage());
            return;
        }

        try (PrintWriter logOut = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            logOut.println("================================================");
            logOut.println("=== 新旧 評価関数 対決 ログ開始 (" + N + "順) ===");

            for (int i = 1; i <= N; i++) {
                System.out.println("【 " + i + " 順目 】");
                
                // 第1戦：旧AI(黒) vs 新AI(白)
                Player oldAsBlack = new OurPlayer(Color.BLACK);
                Player newAsWhite = new OurPlayer2(Color.WHITE);
                int score1 = playMatch(oldAsBlack, newAsWhite, logOut, i, 1);
                
                int ptsOldBlack = calculatePoints(score1);
                int ptsNewWhite = calculatePoints(-score1);
                oldAsBlackPoints += ptsOldBlack;
                newAsWhitePoints += ptsNewWhite;

                System.out.printf("  第1戦 [旧(黒) vs 新(白)] スコア: %d -> 旧AI(黒)獲得: %d点, 新AI(白)獲得: %d点%n", 
                        score1, ptsOldBlack, ptsNewWhite);

                // 第2戦：新AI(黒) vs 旧AI(白)
                Player newAsBlack = new OurPlayer2(Color.BLACK);
                Player oldAsWhite = new OurPlayer(Color.WHITE);
                int score2 = playMatch(newAsBlack, oldAsWhite, logOut, i, 2);
                
                int ptsNewBlack = calculatePoints(score2);
                int ptsOldWhite = calculatePoints(-score2);
                newAsBlackPoints += ptsNewBlack;
                oldAsWhitePoints += ptsOldWhite;

                System.out.printf("  第2戦 [新(黒) vs 旧(白)] スコア: %d -> 新AI(黒)獲得: %d点, 旧AI(白)獲得: %d点%n", 
                        score2, ptsNewBlack, ptsOldWhite);
                System.out.println();
            }

            logOut.println("================================================\n");

        } catch (IOException e) {
            System.err.println("ログファイルへの書き込みに失敗しました: " + e.getMessage());
        }

        // --- 最終結果の集計と出力 ---
        System.out.println("=== 最終結果 (" + (2 * N) + " 対局) ===");
        int totalOld = oldAsBlackPoints + oldAsWhitePoints;
        int totalNew = newAsBlackPoints + newAsWhitePoints;
        
        System.out.println("【旧AI: OurPlayer (重みのみ)】 総ポイント: " + totalOld);
        System.out.println("  - 先手(黒)での獲得ポイント: " + oldAsBlackPoints);
        System.out.println("  - 後手(白)での獲得ポイント: " + oldAsWhitePoints);
        System.out.println();
        System.out.println("【新AI: OurPlayer2 (重み＋モビリティ)】 総ポイント: " + totalNew);
        System.out.println("  - 先手(黒)での獲得ポイント: " + newAsBlackPoints);
        System.out.println("  - 後手(白)での獲得ポイント: " + newAsWhitePoints);

    }

    private static int playMatch(Player black, Player white, PrintWriter logOut, int round, int matchNum) {
        Board board = new MyBoard(); 
        black.setBoard(board.clone());
        white.setBoard(board.clone());

        logOut.println("第" + round + "順 - 第" + matchNum + "戦 対局開始: 先手(黒)=" + black + " vs 後手(白)=" + white);

        while (!board.isEnd()) {
            Color turn = board.getTurn();
            Player currentPlayer = (turn == Color.BLACK) ? black : white;

            ap26.Move move;
            long t0 = System.currentTimeMillis();
            try {
                move = currentPlayer.think(board.clone()).colored(turn);
            } catch (Exception e) {
                System.err.println(currentPlayer + " がエラーを起こしました: " + e.getMessage());
                move = ap26.Move.ofError(turn);
            }
            long duration = System.currentTimeMillis() - t0;

            if (move.isLegal()) {
                board = board.placed(move);
                String logMessage = String.format("%s (%s) の手: %s (思考時間: %.3fs)", 
                        currentPlayer, turn, move, duration / 1000.f);
                logOut.println(logMessage);
            } else {
                String errorMsg = String.format("%s (%s) が反則手を打ちました: %s", currentPlayer, turn, move);
                logOut.println(errorMsg);
                System.err.println(errorMsg);
                board.foul(turn);
                break;
            }
        }
        
        logOut.println("対局終了 スコア: " + board.score());
        logOut.println("--------------------------------------------------");
        logOut.flush();
        
        return board.score();
    }

    // 演習指示書に準拠した勝ち点（ポイント）の計算
    private static int calculatePoints(int score) {
        if (score > 0) return 10 + Math.min(score, 10);
        if (score == 0) return 5;
        return 0; // 負け
    }
}