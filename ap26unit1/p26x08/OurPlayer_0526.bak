package p26x08; // 提出時はチーム番号（例：p26x01）に変更してください

import ap26.*;
import java.util.*;

public class OurPlayer extends Player {
    private static final String PLAYER_NAME = "STRG"; // 他チームと被らない4文字に変更
    private static final float INF = 10000000f;

    // 持ち時間は1ゲームにつき合計60秒 [cite: 93]
    private final long totalTimeMillis = 60000;
    private long timeUsedMillis = 0;
    private boolean timeOut = false;

    public OurPlayer(Color color) {
        super(PLAYER_NAME, color);
    }

    @Override
    public void setBoard(Board board) {
        super.setBoard(board);
        this.timeUsedMillis = 0; // 新しいゲームの開始時に消費時間をリセット
    }

    @Override
    public Move think(Board board) {
        long startTime = System.currentTimeMillis();
        this.timeOut = false;
        Color myColor = getColor();

        List<Move> legalMoves = board.findLegalMoves(myColor);

        // パス、または合法手が1つしかない場合は探索をスキップして即座に返す
        if (legalMoves.isEmpty() || (legalMoves.size() == 1 && legalMoves.get(0).isPass())) {
            return Move.ofPass(myColor);
        }
        if (legalMoves.size() == 1) {
            return legalMoves.get(0);
        }

        // 時間管理: 残り時間から今回の手番に使える時間を動的に割り当て
        long timeLeft = totalTimeMillis - timeUsedMillis;
        long allocatedTime = Math.max(50, timeLeft / 10);

        Move bestMoveThisTurn = legalMoves.get(0);

        // 反復深化法 (Iterative Deepening)
        for (int depth = 1; depth <= 36; depth++) {
            Move bestAtDepth = searchRoot(board.clone(), depth, myColor, startTime, allocatedTime);

            if (timeOut) {
                break; // 割り当て時間を超過したら探索を打ち切り、1つ前の深さでの最善手を採用
            }
            if (bestAtDepth != null) {
                bestMoveThisTurn = bestAtDepth;
            }
        }

        long endTime = System.currentTimeMillis();
        timeUsedMillis += (endTime - startTime);

        return bestMoveThisTurn;
    }

    // 探索のルートノード
    private Move searchRoot(Board board, int depth, Color color, long startTime, long allocatedTime) {
        float alpha = -INF;
        float beta = INF;
        Move best = null;

        List<Move> moves = board.findLegalMoves(color);
        moves = orderMoves(board, moves, color); // 手順並び替え

        for (int i = 0; i < moves.size(); i++) {
            Move m = moves.get(i);
            Board nextBoard = board.placed(m);
            float score;

            // NegaScout法 (PVS: Principal Variation Search)
            if (i == 0) {
                score = -negaScout(nextBoard, depth - 1, -beta, -alpha, color.flipped(), startTime, allocatedTime);
            } else {
                score = -negaScout(nextBoard, depth - 1, -alpha - 1, -alpha, color.flipped(), startTime, allocatedTime);
                if (score > alpha && score < beta) {
                    score = -negaScout(nextBoard, depth - 1, -beta, -score, color.flipped(), startTime, allocatedTime);
                }
            }

            if (timeOut)
                return null;

            if (score > alpha) {
                alpha = score;
                best = m;
            }
        }
        return best;
    }

    // NegaScout法の本体
    private float negaScout(Board board, int depth, float alpha, float beta, Color color, long startTime,
            long allocatedTime) {
        // 時間切れチェック
        if ((System.currentTimeMillis() - startTime) > allocatedTime) {
            timeOut = true;
            return 0;
        }

        if (depth == 0 || board.isEnd()) {
            return evaluate(board, color);
        }

        List<Move> moves = board.findLegalMoves(color);

        if (moves.isEmpty() || (moves.size() == 1 && moves.get(0).isPass())) {
            Board nextBoard = board.placed(Move.ofPass(color));
            return -negaScout(nextBoard, depth - 1, -beta, -alpha, color.flipped(), startTime, allocatedTime);
        }

        moves = orderMoves(board, moves, color);

        for (int i = 0; i < moves.size(); i++) {
            Move m = moves.get(i);
            Board nextBoard = board.placed(m);
            float score;

            if (i == 0) {
                score = -negaScout(nextBoard, depth - 1, -beta, -alpha, color.flipped(), startTime, allocatedTime);
            } else {
                score = -negaScout(nextBoard, depth - 1, -alpha - 1, -alpha, color.flipped(), startTime, allocatedTime);
                if (score > alpha && score < beta) {
                    score = -negaScout(nextBoard, depth - 1, -beta, -score, color.flipped(), startTime, allocatedTime);
                }
            }

            if (timeOut)
                return 0;

            if (score > alpha) {
                alpha = score;
            }
            if (alpha >= beta) {
                break; // ベータカット
            }
        }
        return alpha;
    }

    // 評価関数
    private float evaluate(Board board, Color evalColor) {
        if (board.isEnd()) {
            int score = board.score();
            int colorSign = (evalColor == Color.BLACK) ? 1 : -1;
            return colorSign * score * 1000000f; // 確実な勝敗を最優先
        }

        Color oppColor = evalColor.flipped();

        // 1. マスの重み (静的評価)
        final float[] M = {
                50, -20, 0, 0, -20, 50,
                -20, -40, -5, -5, -40, -20,
                0, -5, 5, 5, -5, 0,
                0, -5, 5, 5, -5, 0,
                -20, -40, -5, -5, -40, -20,
                50, -20, 0, 0, -20, 50
        };

        float posScore = 0;
        for (int k = 0; k < Board.LENGTH; k++) {
            Color c = board.get(k);
            if (c == evalColor)
                posScore += M[k];
            else if (c == oppColor)
                posScore -= M[k];
        }

        // 2. 着手可能手数（機動力）
        int myMobility = board.findLegalMoves(evalColor).size();
        int oppMobility = board.findLegalMoves(oppColor).size();
        float mobilityScore = myMobility - oppMobility;

        // 3. 進行度に基づく重みの動的変化
        int totalStones = board.count(Color.BLACK) + board.count(Color.WHITE);
        float phase = (float) totalStones / Board.LENGTH;

        // 序盤・中盤は機動力重視、終盤は石の差を重視する重み付け
        float w1 = 1.0f; // 盤面位置の重み
        float w2 = 10.0f * (1.0f - phase); // 機動力の重み（終盤に向けて減少）
        float w3 = 5.0f * phase; // 確定石（石数差）の重み（終盤に向けて増加）

        return (w1 * posScore) + (w2 * mobilityScore) + (w3 * (board.count(evalColor) - board.count(oppColor)));
    }

    // 探索する手順の並び替え
    private List<Move> orderMoves(Board board, List<Move> moves, Color color) {
        moves.sort((m1, m2) -> {
            Board b1 = board.placed(m1);
            Board b2 = board.placed(m2);
            // 浅い評価値を用いて良い手から順に並び替え（降順）
            return Float.compare(evaluate(b2, color), evaluate(b1, color));
        });
        return moves;
    }
}