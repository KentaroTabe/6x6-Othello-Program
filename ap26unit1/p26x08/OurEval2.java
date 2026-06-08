package p26x08;

import ap26.Board;
import ap26.Color;
import static ap26.Board.LENGTH;

public class OurEval2 {
    public float[] weights = new float[LENGTH];
    public float[] baseWeights;

    private static final float CORNER_WEIGHT = 50.00f;
    
    // 8方向ベクトル
    private static final int[] DIR_R = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final int[] DIR_C = {-1, 0, 1, -1, 1, -1, 0, 1};

    // --- 動的評価（モビリティ）の学習パラメータ ---
    public float earlyMobilityWeight;
    public float midMobilityWeight;
    public int earlyPhaseThreshold;
    public int midPhaseThreshold;

    // --- 変形盤（BLOCK）対応の学習パラメータ ---
    public float blockOrthogonalMult; 
    public float blockDiagonalMult;   

    // デフォルト値（ベースラインとなる初期値）
    public OurEval2() {
        this(
            new float[]{ -14.29f, 11.80f, -40.00f, -4.25f, 5.00f }, 
            8.0f, 3.0f, 20, 12, 
            -0.8f, -0.4f
        );
    }

    // 全パラメータを受け取るコンストラクタ（進化戦略用）
    public OurEval2(float[] baseWeights, float earlyMw, float midMw, int earlyTh, int midTh, float blockOrthoMult, float blockDiagMult) {
        this.earlyMobilityWeight = earlyMw;
        this.midMobilityWeight = midMw;
        this.earlyPhaseThreshold = earlyTh;
        this.midPhaseThreshold = midTh;
        this.blockOrthogonalMult = blockOrthoMult;
        this.blockDiagonalMult = blockDiagMult;
        init(baseWeights);
    }

    // ディープコピーメソッド（突然変異生成用）
    public OurEval2 copy() {
        return new OurEval2(
            this.baseWeights.clone(),
            this.earlyMobilityWeight,
            this.midMobilityWeight,
            this.earlyPhaseThreshold,
            this.midPhaseThreshold,
            this.blockOrthogonalMult,
            this.blockDiagonalMult
        );
    }

    private void init(float[] base) {
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

    public void initializeForBoard(Board initialBoard) {
        init(this.baseWeights); 
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 6; c++) {
                int idx = r * 6 + c;
                Color color = initialBoard.get(idx);
                
                if (color == Color.BLOCK) {
                    weights[idx] = 0.0f; 
                    
                    for (int d = 0; d < 8; d++) {
                        int nr = r + DIR_R[d];
                        int nc = c + DIR_C[d];
                        if (nr >= 0 && nr < 6 && nc >= 0 && nc < 6) {
                            int nIdx = nr * 6 + nc;
                            boolean isOrthogonal = (DIR_R[d] == 0 || DIR_C[d] == 0);
                            float mult = isOrthogonal ? blockOrthogonalMult : blockDiagonalMult;
                            
                            if (weights[nIdx] < 0) {
                                weights[nIdx] = weights[nIdx] * mult;
                            } else {
                                weights[nIdx] = weights[nIdx] * Math.abs(mult); 
                            }
                        }
                    }
                } else if (color != Color.BLACK && color != Color.WHITE && color != Color.NONE) {
                    weights[idx] = 0.0f; 
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
            if (score >= 10) score = 10;
            if (score <= -10) score = -10;
            return score * 100_000.0f;
        }

        float positionScore = 0;
        int emptyCount = 0;
        
        for (int k = 0; k < LENGTH; k++) {
            Color c = board.get(k);
            if (c == Color.BLACK) positionScore += weights[k];
            else if (c == Color.WHITE) positionScore -= weights[k];
            else if (c == Color.NONE) emptyCount++;
        }

        float mobilityWeight = 0.0f;
        if (emptyCount > earlyPhaseThreshold) {
            mobilityWeight = earlyMobilityWeight;
        } else if (emptyCount > midPhaseThreshold) {
            mobilityWeight = midMobilityWeight;
        }

        if (mobilityWeight > 0.0f) {
            int blackMobility = countMobility(board, Color.BLACK);
            int whiteMobility = countMobility(board, Color.WHITE);
            positionScore += (blackMobility - whiteMobility) * mobilityWeight;
        }

        return positionScore;
    }

    private int countMobility(Board board, Color myColor) {
        int count = 0;
        Color oppColor = (myColor == Color.BLACK) ? Color.WHITE : Color.BLACK;
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 6; c++) {
                if (board.get(r * 6 + c) == Color.NONE) {
                    if (canPlace(board, r, c, myColor, oppColor)) count++;
                }
            }
        }
        return count;
    }

    private boolean canPlace(Board board, int r, int c, Color myColor, Color oppColor) {
        for (int d = 0; d < 8; d++) {
            int nr = r + DIR_R[d];
            int nc = c + DIR_C[d];
            boolean foundOpp = false;
            while (nr >= 0 && nr < 6 && nc >= 0 && nc < 6) {
                Color color = board.get(nr * 6 + nc);
                if (color == oppColor) foundOpp = true;
                else if (color == myColor) {
                    if (foundOpp) return true;
                    break;
                } else break; 
                nr += DIR_R[d];
                nc += DIR_C[d];
            }
        }
        return false;
    }
}