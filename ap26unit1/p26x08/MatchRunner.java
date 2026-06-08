package p26x08;

import ap26.Board;
import ap26.Color;
import ap26.Move;
import ap26.Player;
import myplayer.MyBoard;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 実行時にプレイヤーや出力ファイルを指定できる汎用対戦ランナー
 */
public class MatchRunner {

    public static void main(String[] args) {
        if (args.length < 5) {
            System.out.println("【使用方法】");
            System.out.println("java p26x08.MatchRunner <Player1Class> <Player2Class> <N_Rounds> <ResultFile> <LogFile>");
            System.out.println("例: java p26x08.MatchRunner OurPlayer OurPlayer2 5 result.txt log.txt");
            return;
        }

        String player1ClassName = args[0];
        String player2ClassName = args[1];
        int rounds;
        try {
            rounds = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            System.err.println("対戦順数には整数を指定してください。");
            return;
        }
        String resultFile = args[3];
        String logFile = args[4];

        System.out.println("=== カスタム対戦ランナー ===");
        System.out.println("Player 1 : " + player1ClassName);
        System.out.println("Player 2 : " + player2ClassName);
        System.out.println("対戦順数 : " + rounds + " 順 (計 " + (rounds * 2) + " 局)");
        System.out.println("結果出力 : " + resultFile);
        System.out.println("ログ出力 : " + logFile);
        System.out.println("対戦を開始します...\n");

        // --- 集計用変数 ---
        int p1Points = 0, p2Points = 0;
        
        int p1BlackWins = 0, p1WhiteWins = 0;
        int p2BlackWins = 0, p2WhiteWins = 0;
        int draws = 0;

        // ディレクトリ作成
        try {
            if (Paths.get(resultFile).getParent() != null) {
                Files.createDirectories(Paths.get(resultFile).getParent());
            }
            if (Paths.get(logFile).getParent() != null) {
                Files.createDirectories(Paths.get(logFile).getParent());
            }
        } catch (IOException e) {
            System.err.println("ディレクトリの作成に失敗しました: " + e.getMessage());
            return;
        }

        try (PrintWriter logOut = new PrintWriter(new FileWriter(logFile, false))) {
            logOut.println("================================================");
            logOut.println("=== 対戦ログ開始 (" + rounds + "順) ===");

            for (int i = 1; i <= rounds; i++) {
                System.out.println("【 " + i + " 順目 】");
                
                // 第1戦: Player1(黒) vs Player2(白)
                Player p1AsBlack = createPlayer(player1ClassName, Color.BLACK);
                Player p2AsWhite = createPlayer(player2ClassName, Color.WHITE);
                int score1 = playMatch(p1AsBlack, p2AsWhite, logOut, i, 1);
                
                if (score1 > 0) p1BlackWins++;
                else if (score1 < 0) p2WhiteWins++;
                else draws++;

                p1Points += calculatePoints(score1);
                p2Points += calculatePoints(-score1);

                // 第2戦: Player2(黒) vs Player1(白)
                Player p2AsBlack = createPlayer(player2ClassName, Color.BLACK);
                Player p1AsWhite = createPlayer(player1ClassName, Color.WHITE);
                int score2 = playMatch(p2AsBlack, p1AsWhite, logOut, i, 2);
                
                if (score2 > 0) p2BlackWins++;
                else if (score2 < 0) p1WhiteWins++;
                else draws++;

                p2Points += calculatePoints(score2);
                p1Points += calculatePoints(-score2);
            }
            logOut.println("================================================");
        } catch (Exception e) {
            System.err.println("対局中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // --- 結果の出力 ---
        try (PrintWriter resOut = new PrintWriter(new FileWriter(resultFile, false))) {
            resOut.println("================================================");
            resOut.println("=== 対戦結果レポート ===");
            resOut.println("Player 1: " + player1ClassName);
            resOut.println("Player 2: " + player2ClassName);
            resOut.println("総対局数: " + (rounds * 2));
            resOut.println("引き分け: " + draws);
            resOut.println("================================================");
            resOut.println();
            
            int p1TotalWins = p1BlackWins + p1WhiteWins;
            resOut.println("【Player 1 (" + player1ClassName + ") 成績】");
            resOut.println("総勝利数: " + p1TotalWins + " (勝率: " + String.format("%.2f%%", (double)p1TotalWins / (rounds * 2) * 100) + ")");
            resOut.println("  - 先手(黒)での勝利: " + p1BlackWins + " / " + rounds + " 局");
            resOut.println("  - 後手(白)での勝利: " + p1WhiteWins + " / " + rounds + " 局");
            resOut.println("獲得勝ち点: " + p1Points);
            resOut.println();
            
            int p2TotalWins = p2BlackWins + p2WhiteWins;
            resOut.println("【Player 2 (" + player2ClassName + ") 成績】");
            resOut.println("総勝利数: " + p2TotalWins + " (勝率: " + String.format("%.2f%%", (double)p2TotalWins / (rounds * 2) * 100) + ")");
            resOut.println("  - 先手(黒)での勝利: " + p2BlackWins + " / " + rounds + " 局");
            resOut.println("  - 後手(白)での勝利: " + p2WhiteWins + " / " + rounds + " 局");
            resOut.println("獲得勝ち点: " + p2Points);
            resOut.println("================================================");
            
            System.out.println("全対局が完了しました。結果は " + resultFile + " に保存されました。");
        } catch (IOException e) {
            System.err.println("結果ファイルの書き込みに失敗しました: " + e.getMessage());
        }
    }

    /**
     * クラス名から動的にPlayerインスタンスを生成する
     */
    private static Player createPlayer(String className, Color color) throws Exception {
        // 【修正】パッケージ名が省略されている場合は、自動で p26x08. を付与する
        if (!className.contains(".")) {
            className = "p26x08." + className;
        }
        Class<?> clazz = Class.forName(className);
        return (Player) clazz.getConstructor(Color.class).newInstance(color);
    }

    /**
     * 1局分の対戦を行い、ログを記録する
     */
    private static int playMatch(Player black, Player white, PrintWriter logOut, int round, int matchNum) {
        Board board = new MyBoard(); 
        black.setBoard(board.clone());
        white.setBoard(board.clone());

        logOut.println("第" + round + "順 - 第" + matchNum + "戦 対局開始: 先手(黒)=" + black.getClass().getSimpleName() + 
                       " vs 後手(白)=" + white.getClass().getSimpleName());

        while (!board.isEnd()) {
            Color turn = board.getTurn();
            Player currentPlayer = (turn == Color.BLACK) ? black : white;

            Move move;
            long t0 = System.currentTimeMillis();
            try {
                move = currentPlayer.think(board.clone()).colored(turn);
            } catch (Exception e) {
                System.err.println(currentPlayer.getClass().getSimpleName() + " がエラーを起こしました: " + e.getMessage());
                move = Move.ofError(turn);
            }
            long duration = System.currentTimeMillis() - t0;

            if (move.isLegal()) {
                board = board.placed(move);
                String logMessage = String.format("%s (%s) の手: %s (思考時間: %.3fs)", 
                        currentPlayer.getClass().getSimpleName(), turn, move, duration / 1000.f);
                logOut.println(logMessage);
            } else {
                String errorMsg = String.format("%s (%s) が反則手を打ちました: %s", 
                        currentPlayer.getClass().getSimpleName(), turn, move);
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

    /**
     * 指示書のルールに基づく勝ち点計算
     */
    private static int calculatePoints(int score) {
        if (score > 0) return 10 + Math.min(score, 10);
        if (score == 0) return 5;
        return 0; // 負け
    }
}