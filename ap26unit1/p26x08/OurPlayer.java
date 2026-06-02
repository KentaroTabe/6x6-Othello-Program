package p26x08;

import ap26.*;
import java.util.*;

public class OurPlayer extends Player {
    private OurEval eval;
    
    private long totalConsumedTime = 0;
    private int lastEmptyCount = 36;
    private long currentMoveStartTime;
    private long currentMoveTimeLimit;
    private int nodeCount = 0;
    private static final long MAX_GAME_TIME_MS = 58_000;

    private static class TimeoutException extends Exception {}

    public OurPlayer(Color color) {
        this(color, new OurEval());
    }

    public OurPlayer(Color color, OurEval eval) {
        super("08AA", color); 
        this.eval = eval;
    }

    @Override
    public Move think(Board board) {
        currentMoveStartTime = System.currentTimeMillis();
        nodeCount = 0;
        
        List<Move> myMoves = board.findLegalMoves(getColor());
        if (myMoves.isEmpty() || myMoves.get(0).isPass()) {
            return Move.ofPass(getColor());
        }

        int emptyCount = 0;
        for (int k = 0; k < Board.LENGTH; k++) {
            if (board.get(k) == Color.NONE) emptyCount++;
        }
        
        if (emptyCount >= lastEmptyCount || emptyCount >= 32) {
            totalConsumedTime = 0;
        }
        lastEmptyCount = emptyCount;

        int myRemainingTurns = Math.max(1, emptyCount / 2);
        long timeLeft = MAX_GAME_TIME_MS - totalConsumedTime;
        if (timeLeft < 500) timeLeft = 500; 

        currentMoveTimeLimit = (timeLeft / myRemainingTurns) + 1000;
        
        if (currentMoveTimeLimit > timeLeft - 500) {
            currentMoveTimeLimit = Math.max(100, timeLeft - 500);
        }

        Board searchBoard = getColor() == Color.BLACK ? board.clone() : board.flipped();
        List<Move> searchMoves = new ArrayList<>(searchBoard.findLegalMoves(Color.BLACK));
        
        // ★ 追加: 同スコア時のランダム選択を実現するためのシャッフル
        // 探索順をランダムにすることで、同じ評価値を持つ手の中から無作為に1つが選ばれます
        Collections.shuffle(searchMoves);
        
        Move bestMove = searchMoves.get(0).colored(getColor());

        for (int depth = 1; depth <= 64; depth++) {
            try {
                Move currentBestSearchMove = null;
                float alpha = Float.NEGATIVE_INFINITY;
                float beta = Float.POSITIVE_INFINITY;
                
                Move prevBest = bestMove.colored(Color.BLACK);
                if (searchMoves.remove(prevBest)) {
                    searchMoves.add(0, prevBest);
                }

                for (Move move : searchMoves) {
                    Board nextBoard = searchBoard.placed(move);
                    float val = minSearch(nextBoard, alpha, beta, depth - 1);
                    if (val > alpha) {
                        alpha = val;
                        currentBestSearchMove = move;
                    }
                }
                
                if (currentBestSearchMove != null) {
                    bestMove = currentBestSearchMove.colored(getColor());
                }
                
            } catch (TimeoutException e) {
                break;
            }
        }

        long endTime = System.currentTimeMillis();
        totalConsumedTime += (endTime - currentMoveStartTime);
        
        return bestMove;
    }

    private float maxSearch(Board board, float alpha, float beta, int depth) throws TimeoutException {
        checkTime();
        if (board.isEnd() || depth == 0) return eval.value(board);

        List<Move> moves = board.findLegalMoves(Color.BLACK);
        if (moves.isEmpty() || moves.get(0).isPass()) {
            Board nextBoard = board.placed(Move.ofPass(Color.BLACK));
            return minSearch(nextBoard, alpha, beta, depth - 1);
        }

        for (Move move : moves) {
            Board nextBoard = board.placed(move);
            float val = minSearch(nextBoard, alpha, beta, depth - 1);
            alpha = Math.max(alpha, val);
            if (alpha >= beta) break;
        }
        return alpha;
    }

    private float minSearch(Board board, float alpha, float beta, int depth) throws TimeoutException {
        checkTime();
        if (board.isEnd() || depth == 0) return eval.value(board);

        List<Move> moves = board.findLegalMoves(Color.WHITE);
        if (moves.isEmpty() || moves.get(0).isPass()) {
            Board nextBoard = board.placed(Move.ofPass(Color.WHITE));
            return maxSearch(nextBoard, alpha, beta, depth - 1);
        }

        for (Move move : moves) {
            Board nextBoard = board.placed(move);
            float val = maxSearch(nextBoard, alpha, beta, depth - 1);
            beta = Math.min(beta, val);
            if (alpha >= beta) break;
        }
        return beta;
    }

    private void checkTime() throws TimeoutException {
        nodeCount++;
        if ((nodeCount & 1023) == 0) {
            if (System.currentTimeMillis() - currentMoveStartTime > currentMoveTimeLimit) {
                throw new TimeoutException();
            }
        }
    }
}