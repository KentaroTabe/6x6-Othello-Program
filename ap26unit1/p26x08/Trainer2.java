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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 強化学習（進化戦略）スクリプト
 * フェーズ1: 静的評価パラメータ（盤面位置の重み + BLOCK乗数）を学習
 * [終了条件: 親が50連勝する、または10時間経過]
 * フェーズ2: 動的評価パラメータ（モビリティ重み + フェーズ閾値）を学習
 * [終了条件: 親が50連勝する]
 */
public class Trainer2 {
    private static final Random rand = new Random();
    
    // フェーズ1の制限時間設定 (ミリ秒: 10時間)
    private static final double PHASE1_HOURS = 10.0;
    private static final long PHASE1_DURATION_MS = (long) (PHASE1_HOURS * 60 * 60 * 1000L);

    private static final String WEIGHT_FILE = "../ap26unit1/outputs/evolution_weight.txt";
    private static final String RESULT_FILE = "../ap26unit1/outputs/evolution_result.txt";
    private static final String BETTER_PARAMS_FILE = "../outputs/better_parameters.txt"; // 最強パラメータ出力先
    private static final String RUN_LOG_FILE = "../outputs/training_run.txt"; // 追加: コンソールログ出力先

    static class MatchResult {
        int score;
    }

    // ログをコンソールとファイル両方に出力するためのヘルパーメソッド
    private static void printLog(PrintWriter out, String msg) {
        System.out.println(msg);
        if (out != null) {
            out.println(msg);
            out.flush();
        }
    }

    private static void printfLog(PrintWriter out, String format, Object... args) {
        System.out.printf(format, args);
        if (out != null) {
            out.printf(format, args);
            out.flush();
        }
    }

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        long phase1Limit = startTime + PHASE1_DURATION_MS;

        OurEval2 bestEval = new OurEval2(); // 初期パラメータ

        try {
            Files.createDirectories(Paths.get(WEIGHT_FILE).getParent());
            Files.createDirectories(Paths.get(BETTER_PARAMS_FILE).getParent());
            Files.createDirectories(Paths.get(RUN_LOG_FILE).getParent()); // RUN_LOG用のディレクトリ作成
        } catch (IOException e) {
            System.err.println("ディレクトリ作成失敗: " + e.getMessage());
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(6); // 1世代あたり6局を並列実行

        // runOut を追加し、コンソールと同じ内容を training_run.txt に書き出す
        try (PrintWriter weightOut = new PrintWriter(new FileWriter(WEIGHT_FILE, true));
             PrintWriter resultOut = new PrintWriter(new FileWriter(RESULT_FILE, true));
             PrintWriter runOut = new PrintWriter(new FileWriter(RUN_LOG_FILE, true))) {

            printLog(runOut, "=== 強化学習(進化戦略) 開始 ===");
            printLog(runOut, "標準盤と変形盤(BLOCK)の両方で対戦させて総合力を高めます。");
            printLog(runOut, "連勝記録は " + BETTER_PARAMS_FILE + " に保存されます。");

            weightOut.println("# --- 新しい学習セッション開始 ---");
            resultOut.println("# --- 新しい対戦ログ開始 ---");

            int generation = 1;
            int currentWinStreak = 0;
            int maxWinStreak = 0;

            // ==========================================
            // フェーズ 1: 静的評価パラメータの学習
            // ==========================================
            printLog(runOut, "\n[フェーズ1] 静的評価・BLOCK乗数パラメータの学習を開始");
            weightOut.println("# Phase 1: Static Weights & Block Multipliers");

            while (System.currentTimeMillis() < phase1Limit) {
                OurEval2 mutantEval = bestEval.copy();
                
                // 突然変異の生成 (各要素30%の確率で変異)
                for (int i = 0; i < mutantEval.baseWeights.length; i++) {
                    if (rand.nextDouble() < 0.3) {
                        mutantEval.baseWeights[i] += (rand.nextFloat() * 2 - 1) * 5.0f; // ±5.0
                        mutantEval.baseWeights[i] = Math.max(-80f, Math.min(80f, mutantEval.baseWeights[i]));
                    }
                }
                if (rand.nextDouble() < 0.3) {
                    mutantEval.blockOrthogonalMult += (rand.nextFloat() * 2 - 1) * 0.5f; // ±0.5
                    mutantEval.blockOrthogonalMult = Math.max(-3.0f, Math.min(3.0f, mutantEval.blockOrthogonalMult));
                }
                if (rand.nextDouble() < 0.3) {
                    mutantEval.blockDiagonalMult += (rand.nextFloat() * 2 - 1) * 0.5f;
                    mutantEval.blockDiagonalMult = Math.max(-3.0f, Math.min(3.0f, mutantEval.blockDiagonalMult));
                }

                boolean updated = evaluateMutant(executor, bestEval, mutantEval, generation, resultOut);
                if (updated) {
                    bestEval = mutantEval;
                    currentWinStreak = 0;
                    printfLog(runOut, "P1-Gen %d: 子が勝利！ 重み更新%n", generation);
                } else {
                    currentWinStreak++;
                    printfLog(runOut, "P1-Gen %d: 親が防衛 (連勝: %d)%n", generation, currentWinStreak);
                    
                    if (currentWinStreak > maxWinStreak) {
                        maxWinStreak = currentWinStreak;
                        saveBetterParameters(bestEval, "Phase1", maxWinStreak);
                    }
                    
                    if (currentWinStreak >= 50) {
                        printLog(runOut, "親が50連勝を達成しました。フェーズ1を終了します。");
                        break;
                    }
                }

                weightOut.printf("P1-Gen %d [%s] : Base(%.2f, %.2f, %.2f, %.2f, %.2f) Block(%.2f, %.2f)%n",
                        generation, updated ? "更新" : "維持",
                        bestEval.baseWeights[0], bestEval.baseWeights[1], bestEval.baseWeights[2], bestEval.baseWeights[3], bestEval.baseWeights[4],
                        bestEval.blockOrthogonalMult, bestEval.blockDiagonalMult);
                weightOut.flush();
                
                generation++;
            }

            if (currentWinStreak < 50) {
                printLog(runOut, "10時間が経過したため、フェーズ1を終了します。");
            }

            // ==========================================
            // フェーズ 2: 動的評価パラメータの学習
            // ==========================================
            printLog(runOut, "\n[フェーズ2] 動的評価パラメータの学習を開始");
            weightOut.println("# Phase 2: Dynamic Mobility & Thresholds");

            // 学習切り替え時に連勝記録をリセット。パラメータは引き継ぎ。
            currentWinStreak = 0;
            maxWinStreak = 0;
            generation = 1;

            while (true) {
                OurEval2 mutantEval = bestEval.copy();

                // 突然変異の生成 (各要素30%の確率で変異)
                if (rand.nextDouble() < 0.3) {
                    mutantEval.earlyMobilityWeight += (rand.nextFloat() * 2 - 1) * 2.0f;
                    mutantEval.earlyMobilityWeight = Math.max(0.0f, Math.min(25.0f, mutantEval.earlyMobilityWeight));
                }
                if (rand.nextDouble() < 0.3) {
                    mutantEval.midMobilityWeight += (rand.nextFloat() * 2 - 1) * 2.0f;
                    mutantEval.midMobilityWeight = Math.max(0.0f, Math.min(25.0f, mutantEval.midMobilityWeight));
                }
                if (rand.nextDouble() < 0.3) {
                    mutantEval.earlyPhaseThreshold += rand.nextInt(5) - 2; // ±2
                    mutantEval.earlyPhaseThreshold = Math.max(16, Math.min(30, mutantEval.earlyPhaseThreshold));
                }
                if (rand.nextDouble() < 0.3) {
                    mutantEval.midPhaseThreshold += rand.nextInt(5) - 2;
                    mutantEval.midPhaseThreshold = Math.max(8, Math.min(16, mutantEval.midPhaseThreshold));
                }

                // 論理的な順序を保証
                if (mutantEval.earlyPhaseThreshold <= mutantEval.midPhaseThreshold) {
                    mutantEval.earlyPhaseThreshold = mutantEval.midPhaseThreshold + 2;
                }

                boolean updated = evaluateMutant(executor, bestEval, mutantEval, generation, resultOut);
                if (updated) {
                    bestEval = mutantEval;
                    currentWinStreak = 0;
                    printfLog(runOut, "P2-Gen %d: 子が勝利！ 重み更新%n", generation);
                } else {
                    currentWinStreak++;
                    printfLog(runOut, "P2-Gen %d: 親が防衛 (連勝: %d)%n", generation, currentWinStreak);
                    
                    if (currentWinStreak > maxWinStreak) {
                        maxWinStreak = currentWinStreak;
                        saveBetterParameters(bestEval, "Phase2", maxWinStreak);
                    }
                    
                    if (currentWinStreak >= 50) {
                        printLog(runOut, "親が50連勝を達成しました。フェーズ2を終了します。");
                        break;
                    }
                }

                weightOut.printf("P2-Gen %d [%s] : EarlyW(%.2f) MidW(%.2f) EarlyTh(%d) MidTh(%d)%n",
                        generation, updated ? "更新" : "維持",
                        bestEval.earlyMobilityWeight, bestEval.midMobilityWeight,
                        bestEval.earlyPhaseThreshold, bestEval.midPhaseThreshold);
                weightOut.flush();
                
                generation++;
            }

            printLog(runOut, "\n=== 学習完了 ===");
            printLog(runOut, "最新の重みログは outputs/evolution_weight.txt に、");
            printLog(runOut, "最高連勝記録パラメータは outputs/better_parameters.txt に保存されています。");

        } catch (IOException e) {
            System.err.println("ファイルエラー: " + e.getMessage());
        } finally {
            executor.shutdown();
        }
    }

    /**
     * 自己ベストのパラメータをファイルに追記保存する
     */
    private static void saveBetterParameters(OurEval2 eval, String phase, int streak) {
        try (PrintWriter out = new PrintWriter(new FileWriter(BETTER_PARAMS_FILE, true))) {
            out.printf("[%s] 新記録！ Max Win Streak: %d%n", phase, streak);
            out.printf("BaseWeights: ");
            for (float w : eval.baseWeights) out.printf("%.2f ", w);
            out.println();
            out.printf("BlockOrthoMult: %.2f, BlockDiagMult: %.2f%n", eval.blockOrthogonalMult, eval.blockDiagonalMult);
            out.printf("EarlyMobility: %.2f, MidMobility: %.2f%n", eval.earlyMobilityWeight, eval.midMobilityWeight);
            out.printf("EarlyThreshold: %d, MidThreshold: %d%n", eval.earlyPhaseThreshold, eval.midPhaseThreshold);
            out.println("--------------------------------------------------");
        } catch (IOException e) {
            System.err.println("better_parameters.txt の保存に失敗しました: " + e.getMessage());
        }
    }

    /**
     * 親と子を、標準盤(2局) + 変形盤(4局) の計6局で対戦させ、優劣を評価する
     * @return 子が親を上回った場合に true
     */
    private static boolean evaluateMutant(ExecutorService executor, OurEval2 parentEval, OurEval2 mutantEval, int generation, PrintWriter resultOut) {
        
        Board[] boards = new Board[3];
        boards[0] = new MyBoard(); // 標準盤
        
        int[] blockCandidates = {0, 1, 2, 3, 4, 5, 6, 12, 18, 24, 30};
        for (int i = 1; i <= 2; i++) {
            MyBoard b = new MyBoard();
            int numBlocks = 1 + rand.nextInt(3);
            List<Integer> cands = new ArrayList<>();
            for (int k : blockCandidates) cands.add(k);
            Collections.shuffle(cands, rand);
            for (int j = 0; j < numBlocks; j++) b.set(cands.get(j), Color.BLOCK);
            boards[i] = b;
        }

        List<CompletableFuture<MatchResult>> futures = new ArrayList<>();

        for (int bIdx = 0; bIdx < 3; bIdx++) {
            final Board board = boards[bIdx];
            
            futures.add(CompletableFuture.supplyAsync(() -> {
                // コンストラクタ引数からFAST_GAME_TIME_MSを削除し、デフォルトの大会ルール（58秒）を適用
                Player parentAsBlack = new OurPlayer3(Color.BLACK, parentEval);
                Player childAsWhite = new OurPlayer3(Color.WHITE, mutantEval);
                return playMatch(parentAsBlack, childAsWhite, board);
            }, executor));

            futures.add(CompletableFuture.supplyAsync(() -> {
                Player childAsBlack = new OurPlayer3(Color.BLACK, mutantEval);
                Player parentAsWhite = new OurPlayer3(Color.WHITE, parentEval);
                return playMatch(childAsBlack, parentAsWhite, board);
            }, executor));
        }

        int parentPoints = 0, childPoints = 0;
        int parentTotalScore = 0, childTotalScore = 0;
        StringBuilder scoresLog = new StringBuilder(); // 各対局のスコアを記録

        try {
            for (int i = 0; i < 6; i += 2) {
                MatchResult r1 = futures.get(i).get();     // 親(黒) vs 子(白)
                MatchResult r2 = futures.get(i + 1).get(); // 子(黒) vs 親(白)

                parentPoints += calculatePoints(r1.score) + calculatePoints(-r2.score);
                childPoints += calculatePoints(-r1.score) + calculatePoints(r2.score);
                
                parentTotalScore += (r1.score - r2.score);
                childTotalScore += (-r1.score + r2.score);

                // 1局目 (親視点のスコア:子視点のスコア)
                scoresLog.append(String.format("[%d:%d]", r1.score, -r1.score));
                // 2局目 (親視点のスコア:子視点のスコア)
                scoresLog.append(String.format("[%d:%d]", -r2.score, r2.score));
            }
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("並列対局エラー: " + e.getMessage());
            return false;
        }

        // ラウンド分のスコアを列挙して出力
        resultOut.printf("Gen %d | 勝ち点 [親:%d, 子:%d] | スコア: %s%n",
                generation, parentPoints, childPoints, scoresLog.toString());
        resultOut.flush();
        
        if (childPoints > parentPoints) {
            return true;
        } else if (childPoints == parentPoints && childTotalScore > parentTotalScore) {
            return true;
        }
        return false;
    }

    private static MatchResult playMatch(Player black, Player white, Board initialBoard) {
        Board board = initialBoard.clone(); 
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