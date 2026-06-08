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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 実行時にプレイヤーや出力ファイルを指定できる汎用対戦ランナー
 * 指示書のルールに基づき、1ラウンドあたり「標準盤1枚＋変形盤2枚」の計3盤面を用い、
 * それぞれ先手・後手を入れ替えて計6局の対戦を行います。
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
            System.err.println("対戦ラウンド数には整数を指定してください。");
            return;
        }
        String resultFile = args[3];
        String logFile = args[4];

        int totalGames = rounds * 3 * 2; // ラウンド数 × 3盤面 × 先後2局

        System.out.println("=== カスタム対戦ランナー (変形盤対応) ===");
        System.out.println("Player 1 : " + player1ClassName);
        System.out.println("Player 2 : " + player2ClassName);
        System.out.println("対戦ラウンド : " + rounds + " ラウンド (計 " + totalGames + " 局)");
        System.out.println("結果出力 : " + resultFile);
        System.out.println("ログ出力 : " + logFile);
        System.out.println("対戦を開始します...\n");

        // --- 集計用変数 ---
        int p1Points = 0, p2Points = 0;
        int p1Wins = 0, p2Wins = 0, draws = 0;
        int p1StandardWins = 0, p2StandardWins = 0;
        int p1VariantWins = 0, p2VariantWins = 0;

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

        Random rnd = new Random(); // 盤面生成用

        try (PrintWriter logOut = new PrintWriter(new FileWriter(logFile, false))) {
            logOut.println("================================================");
            logOut.println("=== 対戦ログ開始 (" + rounds + "ラウンド) ===");

            for (int i = 1; i <= rounds; i++) {
                System.out.println("【 " + i + " ラウンド目 】");
                logOut.println("【 " + i + " ラウンド目 】");
                
                // 3つの盤面を生成: [0]標準盤, [1]変形盤1, [2]変形盤2
                Board[] boards = generateRoundBoards(rnd);

                for (int bIdx = 0; bIdx < 3; bIdx++) {
                    Board currentBoard = boards[bIdx];
                    String boardType = (bIdx == 0) ? "標準盤" : "変形盤" + bIdx;
                    
                    // 第1戦: Player1(黒) vs Player2(白)
                    Player p1AsBlack = createPlayer(player1ClassName, Color.BLACK);
                    Player p2AsWhite = createPlayer(player2ClassName, Color.WHITE);
                    int score1 = playMatch(currentBoard, p1AsBlack, p2AsWhite, logOut, i, boardType, 1);
                    
                    if (score1 > 0) { p1Wins++; if(bIdx==0) p1StandardWins++; else p1VariantWins++; }
                    else if (score1 < 0) { p2Wins++; if(bIdx==0) p2StandardWins++; else p2VariantWins++; }
                    else draws++;

                    p1Points += calculatePoints(score1);
                    p2Points += calculatePoints(-score1);

                    // 第2戦: Player2(黒) vs Player1(白)
                    Player p2AsBlack = createPlayer(player2ClassName, Color.BLACK);
                    Player p1AsWhite = createPlayer(player1ClassName, Color.WHITE);
                    int score2 = playMatch(currentBoard, p2AsBlack, p1AsWhite, logOut, i, boardType, 2);
                    
                    if (score2 > 0) { p2Wins++; if(bIdx==0) p2StandardWins++; else p2VariantWins++; }
                    else if (score2 < 0) { p1Wins++; if(bIdx==0) p1StandardWins++; else p1VariantWins++; }
                    else draws++;

                    p2Points += calculatePoints(score2);
                    p1Points += calculatePoints(-score2);
                }
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
            resOut.println("総対局数: " + totalGames);
            resOut.println("引き分け: " + draws);
            resOut.println("================================================");
            resOut.println();
            
            resOut.println("【Player 1 (" + player1ClassName + ") 成績】");
            resOut.println("総勝利数: " + p1Wins + " (勝率: " + String.format("%.2f%%", (double)p1Wins / totalGames * 100) + ")");
            resOut.println("  - 標準盤での勝利: " + p1StandardWins + " / " + (rounds * 2) + " 局");
            resOut.println("  - 変形盤での勝利: " + p1VariantWins + " / " + (rounds * 4) + " 局");
            resOut.println("獲得勝ち点: " + p1Points);
            resOut.println();
            
            resOut.println("【Player 2 (" + player2ClassName + ") 成績】");
            resOut.println("総勝利数: " + p2Wins + " (勝率: " + String.format("%.2f%%", (double)p2Wins / totalGames * 100) + ")");
            resOut.println("  - 標準盤での勝利: " + p2StandardWins + " / " + (rounds * 2) + " 局");
            resOut.println("  - 変形盤での勝利: " + p2VariantWins + " / " + (rounds * 4) + " 局");
            resOut.println("獲得勝ち点: " + p2Points);
            resOut.println("================================================");
            
            System.out.println("全対局が完了しました。結果は " + resultFile + " に保存されました。");
        } catch (IOException e) {
            System.err.println("結果ファイルの書き込みに失敗しました: " + e.getMessage());
        }
    }

    /**
     * 指示書のルールに基づき、1ラウンド用の3つの盤面を生成する
     * [0]: 標準盤, [1], [2]: 変形盤
     */
    private static Board[] generateRoundBoards(Random rnd) {
        Board[] boards = new Board[3];
        boards[0] = new MyBoard(); // 標準盤
        
        // BLOCKの候補位置: a1, b1, c1, d1, e1, f1, a2, a3, a4, a5, a6 (11マス)
        int[] blockCandidates = {0, 1, 2, 3, 4, 5, 6, 12, 18, 24, 30};
        
        for (int i = 1; i <= 2; i++) {
            MyBoard b = new MyBoard();
            int numBlocks = 1 + rnd.nextInt(3); // 1〜3個
            
            List<Integer> cands = new ArrayList<>();
            for (int k : blockCandidates) cands.add(k);
            Collections.shuffle(cands, rnd); // 無作為抽選
            
            for (int j = 0; j < numBlocks; j++) {
                b.set(cands.get(j), Color.BLOCK); // BLOCKを配置
            }
            boards[i] = b;
        }
        return boards;
    }

    private static Player createPlayer(String className, Color color) throws Exception {
        if (!className.contains(".")) {
            className = "p26x08." + className;
        }
        Class<?> clazz = Class.forName(className);
        return (Player) clazz.getConstructor(Color.class).newInstance(color);
    }

    private static int playMatch(Board initialBoard, Player black, Player white, PrintWriter logOut, int round, String boardType, int matchNum) {
        Board board = initialBoard.clone(); 
        black.setBoard(board.clone());
        white.setBoard(board.clone());

        logOut.println("R" + round + "-" + boardType + "-" + matchNum + "戦 先手=" + black.getClass().getSimpleName() + 
                       " vs 後手=" + white.getClass().getSimpleName());

        while (!board.isEnd()) {
            Color turn = board.getTurn();
            Player currentPlayer = (turn == Color.BLACK) ? black : white;

            Move move;
            long t0 = System.currentTimeMillis();
            try {
                move = currentPlayer.think(board.clone()).colored(turn);
            } catch (Exception e) {
                System.err.println(currentPlayer.getClass().getSimpleName() + " がエラー: " + e.getMessage());
                move = Move.ofError(turn);
            }
            long duration = System.currentTimeMillis() - t0;

            if (move.isLegal()) {
                board = board.placed(move);
                logOut.println(String.format("%s (%s): %s (%.3fs)", currentPlayer.getClass().getSimpleName(), turn, move, duration / 1000.f));
            } else {
                String errorMsg = String.format("%s (%s) 反則手: %s", currentPlayer.getClass().getSimpleName(), turn, move);
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

    private static int calculatePoints(int score) {
        if (score > 0) return 10 + Math.min(score, 10);
        if (score == 0) return 5;
        return 0; // 負け
    }
}