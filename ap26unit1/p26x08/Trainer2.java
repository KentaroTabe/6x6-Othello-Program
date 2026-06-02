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
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * 動的評価（モビリティ重みとフェーズ閾値）を最適化するための学習プログラム
 */
public class Trainer2 {
    private static final Random rand = new Random();
    private static final int MAX_GENERATIONS = 500;
    private static final int CONVERGENCE_LIMIT = 50;

    // 変異の幅（浮動小数点用と整数用）
    private static final float MUTATION_RATE_WEIGHT = 2.0f;
    private static final int MUTATION_RATE_THRESHOLD = 2;

    private static final String DYNAMIC_WEIGHT_FILE = "../ap26unit1/outputs/dynamic_weight.txt";
    private static final String DYNAMIC_OUTPUT_FILE = "../ap26unit1/outputs/dynamic_result.txt";

    static class MatchResult {
        int score;
    }

    public static void main(String[] args) {
        // 初期状態：手動で設定した悪くないパラメータ
        OurEval2 bestEval = new OurEval2(); 
        
        // 静的重みは固定しておく（ここでは最適化済みとする）
        float[] fixedBaseWeights = bestEval.baseWeights.clone();
        
        int consecutiveNoUpdates = 0;

        try {
            Files.createDirectories(Paths.get(DYNAMIC_WEIGHT_FILE).getParent());
        } catch (IOException e) {
            System.err.println("ディレクトリ作成失敗: " + e.getMessage());
            return;
        }

        System.out.println("=== 動的評価(モビリティ) パラメータ自動調整開始 ===");

        try (PrintWriter weightOut = new PrintWriter(new FileWriter(DYNAMIC_WEIGHT_FILE, true));
             PrintWriter resultOut = new PrintWriter(new FileWriter(DYNAMIC_OUTPUT_FILE, true))) {

            weightOut.println("# --- 新しい学習セッション (動的評価パラメータ) ---");
            weightOut.println("# 形式: Generation [Status] : earlyWeight, midWeight, earlyThreshold, midThreshold");
            resultOut.println("# --- 新しい対戦ログ ---");

            for (int gen = 1; gen <= MAX_GENERATIONS; gen++) {
                
                // --- 突然変異の生成 ---
                float mutEarlyMw = bestEval.earlyMobilityWeight;
                float mutMidMw = bestEval.midMobilityWeight;
                int mutEarlyTh = bestEval.earlyPhaseThreshold;
                int mutMidTh = bestEval.midPhaseThreshold;

                // 各パラメータを独立して約30%の確率で変異させる
                if (rand.nextDouble() < 0.3) {
                    mutEarlyMw += (rand.nextFloat() * 2 - 1) * MUTATION_RATE_WEIGHT;
                    mutEarlyMw = Math.max(0.0f, Math.min(20.0f, mutEarlyMw)); // 0~20の範囲に収める
                }
                if (rand.nextDouble() < 0.3) {
                    mutMidMw += (rand.nextFloat() * 2 - 1) * MUTATION_RATE_WEIGHT;
                    mutMidMw = Math.max(0.0f, Math.min(20.0f, mutMidMw));
                }
                if (rand.nextDouble() < 0.3) {
                    mutEarlyTh += rand.nextInt(MUTATION_RATE_THRESHOLD * 2 + 1) - MUTATION_RATE_THRESHOLD;
                    mutEarlyTh = Math.max(16, Math.min(28, mutEarlyTh)); // 16~28
                }
                if (rand.nextDouble() < 0.3) {
                    mutMidTh += rand.nextInt(MUTATION_RATE_THRESHOLD * 2 + 1) - MUTATION_RATE_THRESHOLD;
                    mutMidTh = Math.max(8, Math.min(16, mutMidTh)); // 8~16
                }

                // 序盤の閾値は必ず中盤の閾値より大きくする
                if (mutEarlyTh <= mutMidTh) {
                    mutEarlyTh = mutMidTh + 2;
                }

                OurEval2 mutantEval = new OurEval2(fixedBaseWeights, mutEarlyMw, mutMidMw, mutEarlyTh, mutMidTh);

                final OurEval2 finalBestEval = bestEval;
                final OurEval2 finalMutantEval = mutantEval;

                // --- 2局（先手・後手入れ替え）の並列実行 ---
                CompletableFuture<MatchResult> future1 = CompletableFuture.supplyAsync(() -> {
                    Player parentAsBlack = new OurPlayer2(Color.BLACK, finalBestEval);
                    Player childAsWhite = new OurPlayer2(Color.WHITE, finalMutantEval);
                    return playMatch(parentAsBlack, childAsWhite);
                });

                CompletableFuture<MatchResult> future2 = CompletableFuture.supplyAsync(() -> {
                    Player childAsBlack = new OurPlayer2(Color.BLACK, finalMutantEval);
                    Player parentAsWhite = new OurPlayer2(Color.WHITE, finalBestEval);
                    return playMatch(childAsBlack, parentAsWhite);
                });

                MatchResult r1, r2;
                try {
                    r1 = future1.get();
                    r2 = future2.get();
                } catch (InterruptedException | ExecutionException e) {
                    System.err.println("並列対局エラー: " + e.getMessage());
                    break;
                }

                // 指示書ルールに基づく勝ち点計算
                int parentPoints = calculatePoints(r1.score) + calculatePoints(-r2.score);
                int childPoints = calculatePoints(-r1.score) + calculatePoints(r2.score);

                resultOut.printf("世代 %d | 勝ち点 -> 親: %d, 子: %d | 第1戦: %d | 第2戦: %d%n",
                        gen, parentPoints, childPoints, r1.score, r2.score);
                resultOut.flush();

                boolean updated = false;
                if (childPoints > parentPoints) {
                    bestEval = mutantEval;
                    updated = true;
                    consecutiveNoUpdates = 0;
                    System.out.printf("世代 %d: 子が勝ち越し！ 重み更新 (親: %d点, 子: %d点)%n", gen, parentPoints, childPoints);
                } else {
                    consecutiveNoUpdates++;
                    System.out.printf("世代 %d: 親が防衛 (親: %d点, 子: %d点)%n", gen, parentPoints, childPoints);
                }

                // ログへの書き出し (小数点以下2桁)
                weightOut.printf("世代 %d [%s] : %.2f, %.2f, %d, %d%n", 
                        gen, updated ? "更新" : "維持", 
                        bestEval.earlyMobilityWeight, bestEval.midMobilityWeight, 
                        bestEval.earlyPhaseThreshold, bestEval.midPhaseThreshold);
                weightOut.flush();

                if (consecutiveNoUpdates >= CONVERGENCE_LIMIT) {
                    System.out.printf("【収束停止】%d世代連続で改善なし。学習を終了します。%n", CONVERGENCE_LIMIT);
                    break;
                }
            }
            
            System.out.println("=== 最終結果 ===");
            System.out.printf("Early Weight: %.2f%n", bestEval.earlyMobilityWeight);
            System.out.printf("Mid Weight  : %.2f%n", bestEval.midMobilityWeight);
            System.out.printf("Early Phase Threshold: %d%n", bestEval.earlyPhaseThreshold);
            System.out.printf("Mid Phase Threshold  : %d%n", bestEval.midPhaseThreshold);

        } catch (IOException e) {
            System.err.println("ファイルエラー: " + e.getMessage());
        }
    }

    private static MatchResult playMatch(Player black, Player white) {
        Board board = new MyBoard(); 
        black.setBoard(board.clone());
        white.setBoard(board.clone());

        while (!board.isEnd()) {
            Color turn = board.getTurn();
            Player currentPlayer = (turn == Color.BLACK) ? black : white;

            ap26.Move move;
            try {
                move = currentPlayer.think(board.clone()).colored(turn);
            } catch (Exception e) {
                move = ap26.Move.ofError(turn);
            }

            if (move.isLegal()) {
                board = board.placed(move);
            } else {
                board.foul(turn);
                break;
            }
        }

        MatchResult result = new MatchResult();
        result.score = board.score();
        return result;
    }

    private static int calculatePoints(int score) {
        if (score > 0) return 10 + Math.min(score, 10);
        if (score == 0) return 5;
        return 0;
    }
}