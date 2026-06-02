package p26x08;

import ap26.Board;
import ap26.Color;
import static ap26.Board.LENGTH;

public class OurEval2 {
    public float[] weights = new float[LENGTH];
    public float[] baseWeights;

    private static final float CORNER_WEIGHT = 50.00f;
    
    // モビリティ（合法手数の差）にかける重み
    private static final float MOBILITY_WEIGHT = 15.0f;

    public OurEval2() {
        // 先ほど5次元に圧縮した最適化済みの重み
        float[] initBase = {
            -14.29f, 11.80f,
            -40.00f, -4.25f,
             5.00f
        };
        init(initBase);
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

    public float value(Board board) {
        if (board.isEnd()) {
            return 1_000_000 * board.score();
        }

        // 1. 位置の重みによる評価（静的評価）
        float positionScore = 0;
        for (int k = 0; k < LENGTH; k++) {
            Color c = board.get(k);
            if (c == Color.BLACK || c == Color.WHITE) {
                positionScore += weights[k] * c.getValue();
            }
        }

        // 2. モビリティ（着手可能手数）による評価（動的評価）
        int blackMobility = board.findLegalMoves(Color.BLACK).size();
        int whiteMobility = board.findLegalMoves(Color.WHITE).size();
        
        float mobilityScore = (blackMobility - whiteMobility) * MOBILITY_WEIGHT;

        return positionScore + mobilityScore;
    }
}