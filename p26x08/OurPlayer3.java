package p26x08;

import ap26.*;
import java.util.*;

public class OurPlayer3 extends Player {
    private OurEval2 eval;
    
    private long totalConsumedTime = 0;
    private long currentMoveStartTime;
    private long currentMoveTimeLimit;
    private int nodeCount = 0;
    
    // 学習時や状況に応じて制限時間を変更できるようにインスタンス変数化（大会ルールは60秒=60000ms。バッファ込で58秒）
    private long maxGameTimeMs = 58_000;
    
    private static final int TT_SIZE = 1 << 20; 
    private static final int TT_MASK = TT_SIZE - 1;
    private long[] ttHash = new long[TT_SIZE];
    private int[] ttDepth = new int[TT_SIZE];
    private float[] ttValue = new float[TT_SIZE];
    private byte[] ttFlag = new byte[TT_SIZE];
    private int[] ttMove = new int[TT_SIZE];

    private static class TimeoutException extends Exception {}

    public OurPlayer3(Color color) {
        this(color, new OurEval2(), 58_000); 
    }

    public OurPlayer3(Color color, OurEval2 eval) {
        this(color, eval, 58_000); 
    }

    // 進化戦略の高速学習用（早指し）コンストラクタ
    public OurPlayer3(Color color, OurEval2 eval, long maxGameTimeMs) {
        super("our3", color); 
        this.eval = eval;
        this.maxGameTimeMs = maxGameTimeMs;
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
        
        long timeLeft = maxGameTimeMs - totalConsumedTime;
        if (timeLeft < 500) timeLeft = 500; 

        boolean isEndgame = (emptyCount <= 14);
        
        // 持ち時間を限界まで使うアグレッシブな時間配分
        if (isEndgame) {
            // 終盤は残り時間をギリギリまで使う
            currentMoveTimeLimit = timeLeft - (maxGameTimeMs < 10000 ? 50 : 100); 
        } else {
            // 残り手番数の見積もり
            int myRemainingTurns = Math.max(1, emptyCount / 2);
            
            // 均等割りではなく、1.5倍の係数をかけて深読みを優先する（時間を前借りするイメージ）
            currentMoveTimeLimit = (long) ((timeLeft / (double) myRemainingTurns) * 1.5);
            
            // ただし、1手で残り時間の40%以上を使わないようセーフティをかける
            long maxAllowed = (long) (timeLeft * 0.4); 
            if (currentMoveTimeLimit > maxAllowed) {
                currentMoveTimeLimit = maxAllowed;
            }
            
            // 制限時間が長ければ最低保証時間を設定
            if (maxGameTimeMs >= 50000 && currentMoveTimeLimit < 1500) {
                currentMoveTimeLimit = Math.min(1500, timeLeft - 500);
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

        long hash = getNormalizedHash(board, true);
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

        long hash = getNormalizedHash(board, false);
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

    // --- ビットボードによる対称性・正規化ロジック ---

    /**
     * 6x6の盤面を 8x8ビットボード形式 (A1が最下位ビット、各行8ビット) の左上6x6にマッピングする。
     * @return long配列 [黒のビットボード, 白のビットボード]
     */
    private long[] toBitboards(Board board) {
        long b = 0L;
        long w = 0L;
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 6; c++) {
                Color color = board.get(r * 6 + c);
                if (color == Color.BLACK) {
                    b |= (1L << (r * 8 + c));
                } else if (color == Color.WHITE) {
                    w |= (1L << (r * 8 + c));
                }
            }
        }
        return new long[]{b, w};
    }

    // 盤面の上下反転
    private long flipVertical(long b) {
        long x = Long.reverseBytes(b);
        // 上下反転により、元の上6行が下6行に移動してしまうため、
        // 2行分（16ビット）右にシフトして元の左上の領域(0~5行)に戻す。
        return x >>> 16;
    }

    // 盤面の左右反転
    private long flipHorizontal(long b) {
        long k1 = 0x5555555555555555L;
        long k2 = 0x3333333333333333L;
        long k4 = 0x0f0f0f0f0f0f0f0fL;
        long x = b;
        x = ((x >>> 1) & k1) | ((x & k1) << 1);
        x = ((x >>> 2) & k2) | ((x & k2) << 2);
        x = ((x >>> 4) & k4) | ((x & k4) << 4);
        // 左右反転により、元の左6列が右6列に移動してしまうため、
        // 2列分（2ビット）右にシフトして元の左側の領域(0~5列)に戻す。
        return x >>> 2;
    }

    // 盤面の対角線反転 (左上から右下への対角線 \)
    private long transpose(long b) {
        long x = b;
        long t;
        long k1 = 0x00AA00AA00AA00AAL;
        long k2 = 0x0000CCCC0000CCCCL;
        long k3 = 0x00000000F0F0F0F0L;
        t = (x ^ (x >>>  7)) & k1; x = x ^ t ^ (t <<  7);
        t = (x ^ (x >>> 14)) & k2; x = x ^ t ^ (t << 14);
        t = (x ^ (x >>> 28)) & k3; x = x ^ t ^ (t << 28);
        // 対角線 \ は左上(A1)を起点とするため、6x6領域は移動せず位置の補正は不要。
        return x;
    }

    /**
     * 対称形を考慮した「正規化されたハッシュ値」を計算する。
     * 8つの対称形の中で、ビットボードの値が最小となる盤面を代表局面とし、
     * その代表局面からハッシュを生成する。
     */
    private long getNormalizedHash(Board board, boolean isBlackTurn) {
        long[] bw = toBitboards(board);
        long b = bw[0];
        long w = bw[1];

        // 3つの基本変形
        long b_fv = flipVertical(b);      long w_fv = flipVertical(w);
        long b_fh = flipHorizontal(b);    long w_fh = flipHorizontal(w);
        long b_tr = transpose(b);         long w_tr = transpose(w);

        // 8つの対称形を生成
        long[] b_sym = new long[8];
        long[] w_sym = new long[8];

        b_sym[0] = b;                            w_sym[0] = w;
        b_sym[1] = b_fv;                         w_sym[1] = w_fv;
        b_sym[2] = b_fh;                         w_sym[2] = w_fh;
        b_sym[3] = flipVertical(b_fh);           w_sym[3] = flipVertical(w_fh); // 180度回転
        b_sym[4] = b_tr;                         w_sym[4] = w_tr;
        b_sym[5] = flipHorizontal(b_tr);         w_sym[5] = flipHorizontal(w_tr); // 90度回転
        b_sym[6] = flipVertical(b_tr);           w_sym[6] = flipVertical(w_tr);   // 270度回転
        b_sym[7] = flipVertical(b_sym[5]);       w_sym[7] = flipVertical(w_sym[5]); // 逆対角線反転

        // 辞書順で最小のペア（代表局面）を見つける
        long minB = b_sym[0];
        long minW = w_sym[0];
        for (int i = 1; i < 8; i++) {
            if (b_sym[i] < minB || (b_sym[i] == minB && w_sym[i] < minW)) {
                minB = b_sym[i];
                minW = w_sym[i];
            }
        }

        // 代表局面から強いハッシュ関数（MurmurHash3風）でハッシュ値を生成
        return mixHash(minB, minW, isBlackTurn);
    }

    // ビットボードから直接ハッシュを計算する（Zobrist配列の代替）
    private long mixHash(long b, long w, boolean isBlackTurn) {
        long h = b ^ (w + 0x9E3779B97F4A7C15L + (b << 6) + (b >>> 2));
        if (!isBlackTurn) h ^= 0x123456789ABCDEFL;
        
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
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