package p26x08;

import ap26.Board;
import ap26.Color;
import static ap26.Board.LENGTH;

public class OurEval {
    public float[] weights = new float[LENGTH];
    public float[] baseWeights;

    private static final float CORNER_WEIGHT = 50.00f;
    
    // 8方向のベクトル (変形盤のBLOCK隣接判定用)
    protected static final int[] DIR_R = {-1, -1, -1, 0, 0, 1, 1, 1};
    protected static final int[] DIR_C = {-1, 0, 1, -1, 1, -1, 0, 1};

    public OurEval() {
        float[] initBase = {
            -14.29f, 11.80f,
            -40.00f, -4.25f,
             5.00f
        };
        init(initBase);
    }

    public OurEval(float[] baseWeights) {
        init(baseWeights);
    }

    protected void init(float[] base) {
        this.baseWeights = base.clone();
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 6; c++) {
                int mappedR = (r < 3) ? r : 5 - r;
                int mappedC = (c < 3) ? c : 5 - c;
                if (mappedR > mappedC) {
                    int temp = mappedR;
                    mappedR = mappedC;
                    mappedC = temp;
                }
                
                if (mappedR == 0 && mappedC == 0) {
                    weights[r * 6 + c] = CORNER_WEIGHT;
                } else {
                    int index = 0;
                    if (mappedR == 0) index = mappedC - 1;
                    else if (mappedR == 1) index = 1 + mappedC;
                    else if (mappedR == 2) index = 4;
                    weights[r * 6 + c] = base[index];
                }
            }
        }
    }

    // 試合開始時に盤面を受け取り、BLOCKマスを考慮して重みを動的再構築する
    public void initializeForBoard(Board initialBoard) {
        init(this.baseWeights); // まず基本重みでリセット
        
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 6; c++) {
                int idx = r * 6 + c;
                Color color = initialBoard.get(idx);
                
                // BLACK, WHITE, NONE でないマスは BLOCK とみなす
                if (color != null && color != Color.BLACK && color != Color.WHITE && color != Color.NONE) {
                    weights[idx] = 0.0f; 
                    
                    // BLOCKに隣接するマスのX打ちペナルティを緩和する
                    for (int d = 0; d < 8; d++) {
                        int nr = r + DIR_R[d];
                        int nc = c + DIR_C[d];
                        if (nr >= 0 && nr < 6 && nc >= 0 && nc < 6) {
                            int nIdx = nr * 6 + nc;
                            if (weights[nIdx] < 0) {
                                weights[nIdx] = Math.abs(weights[nIdx]) * 0.5f; 
                            }
                        }
                    }
                }
            }
        }
    }

    public float value(Board board) {
        if (board.isEnd()) {
            int score = board.score();
            // 勝ち点ルール特化の枝刈りのためのスコアクリップ
            if (score >= 10) score = 10;
            if (score <= -10) score = -10;
            return score * 100_000.0f;
        }

        float positionScore = 0;
        for (int k = 0; k < LENGTH; k++) {
            Color c = board.get(k);
            if (c == Color.BLACK) {
                positionScore += weights[k];
            } else if (c == Color.WHITE) {
                positionScore -= weights[k];
            }
        }

        // 静的評価のみを返す（モビリティ評価は行わない）
        return positionScore;
    }
}