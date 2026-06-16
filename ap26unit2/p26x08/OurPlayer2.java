package p26x08;

import ap26.*;
import java.util.*;

public class OurPlayer2 extends Player {
    private OurEval2 eval;
    
    private long totalConsumedTime = 0;
    private long currentMoveStartTime;
    private long currentMoveTimeLimit;
    private int nodeCount = 0;
    
    // 本番環境用の固定制限時間 (バッファ込み)
    private static final long MAX_GAME_TIME_MS = 70_000;
    
    private static final long[][] ZOBRIST = new long[Board.LENGTH][2];
    static {
        Random rnd = new Random(2026);
        for (int i = 0; i < Board.LENGTH; i++) {
            ZOBRIST[i][0] = rnd.nextLong(); // 黒石用乱数
            ZOBRIST[i][1] = rnd.nextLong(); // 白石用乱数
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

    // 本番環境用にコンストラクタを統合・簡略化
    public OurPlayer2(Color color) {
        super("our2", color); 
        this.eval = new OurEval2();
    }

    @Override
    public void setBoard(Board board) {
        this.eval.initializeForBoard(board);
        this.totalConsumedTime = 0;
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
        
        long timeLeft = Math.max(500, MAX_GAME_TIME_MS - totalConsumedTime);

        // --- isEndgame 変数を復活 ---
        boolean isEndgame = (emptyCount <= 14);
        
        if (isEndgame) {
            // 終盤: 通信バッファ(100ms)を残して残り時間を使い切る
            currentMoveTimeLimit = Math.max(100, timeLeft - 100); 
        } else {
            // 序盤・中盤: 残り手番で均等割りした1.5倍を目標時間とする
            int myRemainingTurns = Math.max(1, emptyCount / 2);
            long targetTime = (long) ((timeLeft * 1.5) / myRemainingTurns);
            
            // 上限を残り時間の40%、下限を最低保証時間とする
            long maxAllowed = (long) (timeLeft * 0.4); 
            long minGuaranteed = Math.min(1500, timeLeft - 500);
            
            currentMoveTimeLimit = Math.max(minGuaranteed, Math.min(targetTime, maxAllowed));
        }

        Board searchBoard = getColor() == Color.BLACK ? board.clone() : board.flipped();
        List<Move> searchMoves = new ArrayList<>(searchBoard.findLegalMoves(Color.BLACK));
        
        Move bestMove = searchMoves.get(0).colored(getColor());
        int maxDepth = 36; // 最大深さを36に最適化

        // 初期盤面のルートハッシュを計算 (探索開始時は常に黒番なので true)
        long rootHash = getInitialHash(searchBoard, true);

        for (int depth = 1; depth <= maxDepth; depth++) {
            try {
                Move currentBestSearchMove = null;
                float alpha = Float.NEGATIVE_INFINITY;
                float beta = Float.POSITIVE_INFINITY;
                
                Move prevBest = bestMove.colored(Color.BLACK);
                sortMoves(searchMoves, prevBest);

                for (Move move : searchMoves) {
                    Board nextBoard = searchBoard.placed(move);
                    // 置いた石・ひっくり返った石の差分をXORして次のハッシュを計算
                    long nextHash = updateHash(rootHash, searchBoard, nextBoard);
                    
                    float val = minSearch(nextBoard, nextHash, alpha, beta, depth - 1);
                    if (val > alpha) {
                        alpha = val;
                        currentBestSearchMove = move;
                    }
                }
                
                if (currentBestSearchMove != null) {
                    bestMove = currentBestSearchMove.colored(getColor());
                }
                
                // isEndgame による早期ブレイクが正常に機能します
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

    private float maxSearch(Board board, long hash, float alpha, float beta, int depth) throws TimeoutException {
        checkTime();
        if (board.isEnd() || depth == 0) return eval.value(board);

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
            // パス時のハッシュ更新（手番のXORのみ）
            long nextHash = hash ^ 0x123456789ABCDEFL;
            float val = minSearch(nextBoard, nextHash, alpha, beta, depth - 1);
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
            // 差分XORで次のハッシュを計算
            long nextHash = updateHash(hash, board, nextBoard);
            
            float val = minSearch(nextBoard, nextHash, alpha, beta, depth - 1);
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

    private float minSearch(Board board, long hash, float alpha, float beta, int depth) throws TimeoutException {
        checkTime();
        if (board.isEnd() || depth == 0) return eval.value(board);

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
            // パス時のハッシュ更新（手番のXORのみ）
            long nextHash = hash ^ 0x123456789ABCDEFL;
            float val = maxSearch(nextBoard, nextHash, alpha, beta, depth - 1);
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
            // 差分XORで次のハッシュを計算
            long nextHash = updateHash(hash, board, nextBoard);
            
            float val = maxSearch(nextBoard, nextHash, alpha, beta, depth - 1);
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

    // 各探索の開始時に1回だけ呼ばれる、初期ハッシュ生成用関数
    private long getInitialHash(Board board, boolean isBlackTurn) {
        long hash = 0;
        for (int i = 0; i < Board.LENGTH; i++) {
            Color c = board.get(i);
            if (c == Color.BLACK) hash ^= ZOBRIST[i][0];
            else if (c == Color.WHITE) hash ^= ZOBRIST[i][1];
        }
        if (!isBlackTurn) hash ^= 0x123456789ABCDEFL;
        return hash;
    }

    // --- Zobrist Hash を差分更新（XOR）するメソッド ---
    private long updateHash(long currentHash, Board before, Board after) {
        long hash = currentHash;
        
        // 手番の入れ替え（常にビット反転）
        hash ^= 0x123456789ABCDEFL;
        
        // 変化があったマス（置いたマス ＋ ひっくり返ったマス）だけを抽出してXOR
        for (int i = 0; i < Board.LENGTH; i++) {
            Color cBefore = before.get(i);
            Color cAfter = after.get(i);
            
            if (cBefore != cAfter) {
                // 1. 古い状態の石のハッシュを消去 (XOR)
                if (cBefore == Color.BLACK) hash ^= ZOBRIST[i][0];
                else if (cBefore == Color.WHITE) hash ^= ZOBRIST[i][1];
                
                // 2. 新しい状態の石のハッシュを反映 (XOR)
                if (cAfter == Color.BLACK) hash ^= ZOBRIST[i][0];
                else if (cAfter == Color.WHITE) hash ^= ZOBRIST[i][1];
            }
        }
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