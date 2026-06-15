package p26x08;

import ap26.*;
import java.util.*;

public class OurPlayer1 extends Player {
    private OurEval eval;
    
    private long totalConsumedTime = 0;
    private int lastEmptyCount = 36;
    private long currentMoveStartTime;
    private long currentMoveTimeLimit;
    private int nodeCount = 0;
    private static final long MAX_GAME_TIME_MS = 58_000;
    
    private static final long[][] ZOBRIST = new long[Board.LENGTH][2];
    static {
        Random rnd = new Random(2026);
        for (int i = 0; i < Board.LENGTH; i++) {
            ZOBRIST[i][0] = rnd.nextLong(); 
            ZOBRIST[i][1] = rnd.nextLong(); 
        }
    }
    
    private static final int TT_SIZE = 1 << 20; 
    private static final int TT_MASK = TT_SIZE - 1;
    private long[] ttHash = new long[TT_SIZE];
    private int[] ttDepth = new int[TT_SIZE];
    private float[] ttValue = new float[TT_SIZE];
    private byte[] ttFlag = new byte[TT_SIZE];
    private int[] ttMove = new int[TT_SIZE];

    private static class TimeoutException extends Exception {}

    public OurPlayer1(Color color) {
        this(color, new OurEval());
    }

    public OurPlayer1(Color color, OurEval eval) {
        super("our1", color); 
        this.eval = eval;
    }

    @Override
    public void setBoard(Board board) {
        // 変形盤対応の重み再構築
        this.eval.initializeForBoard(board);
        
        this.totalConsumedTime = 0;
        this.lastEmptyCount = 36;
        this.ttHash = new long[TT_SIZE];
        this.ttFlag = new byte[TT_SIZE];
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
        
        long timeLeft = MAX_GAME_TIME_MS - totalConsumedTime;
        if (timeLeft < 500) timeLeft = 500; 

        boolean isEndgame = (emptyCount <= 14);
        
        if (isEndgame) {
            currentMoveTimeLimit = timeLeft - 200; 
        } else {
            int myRemainingTurns = Math.max(1, emptyCount / 2);
            currentMoveTimeLimit = (timeLeft / myRemainingTurns) + 1000;
            if (currentMoveTimeLimit > timeLeft - 500) {
                currentMoveTimeLimit = Math.max(100, timeLeft - 500);
            }
        }

        Board searchBoard = getColor() == Color.BLACK ? board.clone() : board.flipped();
        List<Move> searchMoves = new ArrayList<>(searchBoard.findLegalMoves(Color.BLACK));
        
        Move bestMove = searchMoves.get(0).colored(getColor());
        int maxDepth = 64;

        for (int depth = 1; depth <= maxDepth; depth++) {
            try {
                Move currentBestSearchMove = null;
                float alpha = Float.NEGATIVE_INFINITY;
                float beta = Float.POSITIVE_INFINITY;
                
                Move prevBest = bestMove.colored(Color.BLACK);
                sortMoves(searchMoves, prevBest);

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
                
                if (isEndgame && (alpha >= 100000.0f || alpha <= -100000.0f)) {
                    break; 
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

        long hash = getHash(board, true);
        int index = (int) (hash & TT_MASK);
        int bestMoveIndex = -1;

        if (ttFlag[index] != 0 && ttHash[index] == hash) {
            bestMoveIndex = ttMove[index]; 
            if (ttDepth[index] >= depth) {
                float val = ttValue[index];
                if (ttFlag[index] == 1) return val;
                if (ttFlag[index] == 2 && val >= beta) return val;
                if (ttFlag[index] == 3 && val <= alpha) return val;
            }
        }

        float alphaOrig = alpha;
        List<Move> moves = new ArrayList<>(board.findLegalMoves(Color.BLACK));
        if (moves.isEmpty() || moves.get(0).isPass()) {
            Board nextBoard = board.placed(Move.ofPass(Color.BLACK));
            float val = minSearch(nextBoard, alpha, beta, depth - 1);
            storeTT(hash, depth, val, alphaOrig, beta, null);
            return val;
        }

        Move ttBestMove = null;
        for(Move m : moves) {
            if(m.getIndex() == bestMoveIndex) { ttBestMove = m; break; }
        }
        sortMoves(moves, ttBestMove);

        float bestVal = Float.NEGATIVE_INFINITY;
        Move currentBestMove = null;
        for (Move move : moves) {
            Board nextBoard = board.placed(move);
            float val = minSearch(nextBoard, alpha, beta, depth - 1);
            if (val > bestVal) {
                bestVal = val;
                currentBestMove = move;
            }
            alpha = Math.max(alpha, bestVal);
            if (alpha >= beta) break;
        }
        
        storeTT(hash, depth, bestVal, alphaOrig, beta, currentBestMove);
        return bestVal;
    }

    private float minSearch(Board board, float alpha, float beta, int depth) throws TimeoutException {
        checkTime();
        if (board.isEnd() || depth == 0) return eval.value(board);

        long hash = getHash(board, false);
        int index = (int) (hash & TT_MASK);
        int bestMoveIndex = -1;

        if (ttFlag[index] != 0 && ttHash[index] == hash) {
            bestMoveIndex = ttMove[index];
            if (ttDepth[index] >= depth) {
                float val = ttValue[index];
                if (ttFlag[index] == 1) return val;
                if (ttFlag[index] == 2 && val >= beta) return val;
                if (ttFlag[index] == 3 && val <= alpha) return val;
            }
        }

        float betaOrig = beta;
        List<Move> moves = new ArrayList<>(board.findLegalMoves(Color.WHITE));
        if (moves.isEmpty() || moves.get(0).isPass()) {
            Board nextBoard = board.placed(Move.ofPass(Color.WHITE));
            float val = maxSearch(nextBoard, alpha, beta, depth - 1);
            storeTT(hash, depth, val, alpha, betaOrig, null);
            return val;
        }
        
        Move ttBestMove = null;
        for(Move m : moves) {
            if(m.getIndex() == bestMoveIndex) { ttBestMove = m; break; }
        }
        sortMoves(moves, ttBestMove);

        float bestVal = Float.POSITIVE_INFINITY;
        Move currentBestMove = null;
        for (Move move : moves) {
            Board nextBoard = board.placed(move);
            float val = maxSearch(nextBoard, alpha, beta, depth - 1);
            if (val < bestVal) {
                bestVal = val;
                currentBestMove = move;
            }
            beta = Math.min(beta, bestVal);
            if (alpha >= beta) break;
        }
        
        storeTT(hash, depth, bestVal, alpha, betaOrig, currentBestMove);
        return bestVal;
    }

    private void sortMoves(List<Move> moves, Move bestMove) {
        moves.sort((m1, m2) -> {
            if (bestMove != null) {
                if (m1.equals(bestMove)) return -1;
                if (m2.equals(bestMove)) return 1;
            }
            float score1 = eval.weights[m1.getIndex()];
            float score2 = eval.weights[m2.getIndex()];
            return Float.compare(score2, score1);
        });
    }

    private void storeTT(long hash, int depth, float val, float alphaOrig, float beta, Move bestMove) {
        int index = (int) (hash & TT_MASK);
        byte flag = 1; 
        if (val <= alphaOrig) flag = 3;      
        else if (val >= beta) flag = 2; 
        
        ttHash[index] = hash;
        ttDepth[index] = depth;
        ttValue[index] = val;
        ttFlag[index] = flag;
        ttMove[index] = (bestMove != null) ? bestMove.getIndex() : -1;
    }

    private long getHash(Board board, boolean isBlackTurn) {
        long hash = 0;
        for (int i = 0; i < Board.LENGTH; i++) {
            Color c = board.get(i);
            if (c == Color.BLACK) hash ^= ZOBRIST[i][0];
            else if (c == Color.WHITE) hash ^= ZOBRIST[i][1];
        }
        if (!isBlackTurn) hash ^= 0x123456789ABCDEFL;
        return hash;
    }

    private void checkTime() throws TimeoutException {
        nodeCount++;
        if ((nodeCount & 4095) == 0) { 
            if (System.currentTimeMillis() - currentMoveStartTime > currentMoveTimeLimit) {
                throw new TimeoutException();
            }
        }
    }
}