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

public class Trainer {
    private static final Random rand = new Random();
    private static final int MAX_GENERATIONS = 500;
    private static final float MUTATION_RATE = 5.0f;
    private static final int CONVERGENCE_LIMIT = 40;

    private static final String WEIGHT_FILE = "../ap26unit1/outputs/weight.txt";
    private static final String OUTPUT_FILE = "../ap26unit1/outputs/result.txt";

    static class MatchResult {
        int score;
        float blackTime;
        float whiteTime;
    }

    public static void main(String[] args) {
        OurEval bestEval = new OurEval();
        int consecutiveNoUpdates = 0;

        try {
            Files.createDirectories(Paths.get(WEIGHT_FILE).getParent());
            Files.createDirectories(Paths.get(OUTPUT_FILE).getParent());
        } catch (IOException e) {
            System.err.println("ディレクトリ作成失敗: " + e.getMessage());
            return;
        }

        System.out.println("=== 評価関数自動調整プロセス開始 (5次元・角固定) ===");

        try (PrintWriter weightOut = new PrintWriter(new FileWriter(WEIGHT_FILE, true));
             PrintWriter resultOut = new PrintWriter(new FileWriter(OUTPUT_FILE, true))) {

            weightOut.println("# --- 新しい学習セッション開始 (5次元パラメータ・角固定) ---");
            resultOut.println("# --- 新しい対戦ログ開始 ---");

            for (int gen = 1; gen <= MAX_GENERATIONS; gen++) {
                
                // 突然変異対象は5つだけ
                float[] mutantBase = bestEval.baseWeights.clone();
                for (int i = 0; i < mutantBase.length; i++) {
                    if (rand.nextDouble() < 0.3) { 
                        mutantBase[i] += (rand.nextFloat() * 2 - 1) * MUTATION_RATE;
                    }
                }
                OurEval mutantEval = new OurEval(mutantBase);

                final OurEval finalBestEval = bestEval;
                final OurEval finalMutantEval = mutantEval;

                CompletableFuture<MatchResult> future1 = CompletableFuture.supplyAsync(() -> {
                    Player parentAsBlack = new OurPlayer(Color.BLACK, finalBestEval);
                    Player childAsWhite = new OurPlayer(Color.WHITE, finalMutantEval);
                    return playMatch(parentAsBlack, childAsWhite);
                });

                CompletableFuture<MatchResult> future2 = CompletableFuture.supplyAsync(() -> {
                    Player childAsBlack = new OurPlayer(Color.BLACK, finalMutantEval);
                    Player parentAsWhite = new OurPlayer(Color.WHITE, finalBestEval);
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

                int parentPoints = calculatePoints(r1.score) + calculatePoints(-r2.score);
                int childPoints = calculatePoints(-r1.score) + calculatePoints(r2.score);

                resultOut.printf("世代 %d | 勝ち点 -> 親: %d, 子: %d | 第1戦: %d (親: %.3fs, 子: %.3fs) | 第2戦: %d (子: %.3fs, 親: %.3fs)%n",
                        gen, parentPoints, childPoints, r1.score, r1.blackTime, r1.whiteTime, r2.score, r2.blackTime, r2.whiteTime);
                resultOut.flush();

                boolean updated = false;
                if (childPoints > parentPoints) {
                    bestEval = mutantEval;
                    updated = true;
                    consecutiveNoUpdates = 0;
                    System.out.printf("世代 %d: 子が勝ち越し。重み更新！(親: %d点, 子: %d点)%n", gen, parentPoints, childPoints);
                } else {
                    consecutiveNoUpdates++;
                    System.out.printf("世代 %d: 親が防衛。(親: %d点, 子: %d点)%n", gen, parentPoints, childPoints);
                }

                weightOut.printf("世代 %d [%s] : ", gen, updated ? "更新" : "維持");
                for (int i = 0; i < bestEval.baseWeights.length; i++) {
                    weightOut.printf("%.2f%s", bestEval.baseWeights[i], (i == bestEval.baseWeights.length - 1) ? "" : ",");
                }
                weightOut.println();
                weightOut.flush();

                if (consecutiveNoUpdates >= CONVERGENCE_LIMIT) {
                    System.out.printf("【収束停止】%d世代連続で改善なし。学習を終了します。%n", CONVERGENCE_LIMIT);
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("ファイルエラー: " + e.getMessage());
        }
    }

    private static MatchResult playMatch(Player black, Player white) {
        Board board = new MyBoard(); 
        black.setBoard(board.clone());
        white.setBoard(board.clone());

        long blackTimeMs = 0;
        long whiteTimeMs = 0;

        while (!board.isEnd()) {
            Color turn = board.getTurn();
            Player currentPlayer = (turn == Color.BLACK) ? black : white;

            long t0 = System.currentTimeMillis();
            ap26.Move move;
            try {
                move = currentPlayer.think(board.clone()).colored(turn);
            } catch (Exception e) {
                move = ap26.Move.ofError(turn);
            }
            long t1 = System.currentTimeMillis();
            long duration = Math.max(t1 - t0, 1);

            if (turn == Color.BLACK) blackTimeMs += duration;
            else whiteTimeMs += duration;

            if (move.isLegal()) {
                board = board.placed(move);
            } else {
                board.foul(turn);
                break;
            }
        }

        MatchResult result = new MatchResult();
        result.score = board.score();
        result.blackTime = blackTimeMs / 1000.f;
        result.whiteTime = whiteTimeMs / 1000.f;
        return result;
    }

    private static int calculatePoints(int score) {
        if (score > 0) return 10 + Math.min(score, 10);
        if (score == 0) return 5;
        return 0;
    }
}