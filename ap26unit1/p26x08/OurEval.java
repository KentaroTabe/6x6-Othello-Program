package p26x08;

import ap26.Board;
import ap26.Color;
import static ap26.Board.LENGTH;

public class OurEval {
    public float[] weights = new float[LENGTH];
    
    // 学習・進化用（角を除外した、計5次元のパラメータ）
    public float[] baseWeights;

    // 角の評価値（固定）
    private static final float CORNER_WEIGHT = 50.00f;

    public OurEval() {
        // 角(0,0)を除外し、残りの5つのパラメータを初期値として設定
        // 0: (0,1) 及び (1,0)
        // 1: (0,2) 及び (2,0)
        // 2: (1,1)
        // 3: (1,2) 及び (2,1)
        // 4: (2,2)
        float[] initBase = {
            -12.61f, 14.13f,
            -45.42f, -1.46f,
             10.30f
        };
        init(initBase);
    }

    public OurEval(float[] baseWeights) {
        init(baseWeights);
    }

    // 5次元の重み＋固定の角を、対称性を利用して36マスに展開するメソッド
    private void init(float[] base) {
        this.baseWeights = base.clone();
        
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 6; c++) {
                // 1. 上下左右の対称性で左上の3x3に折りたたむ
                int mappedR = (r < 3) ? r : 5 - r;
                int mappedC = (c < 3) ? c : 5 - c;
                
                // 2. 斜めの対称性で、必ず mappedR <= mappedC となるようにする
                if (mappedR > mappedC) {
                    int temp = mappedR;
                    mappedR = mappedC;
                    mappedC = temp;
                }
                
                // 3. マスに応じた重みの代入
                if (mappedR == 0 && mappedC == 0) {
                    // 角の場合は固定値を代入し、学習の対象外とする
                    weights[r * 6 + c] = CORNER_WEIGHT;
                } else {
                    // 角以外のマスは、5次元配列からインデックスを計算して取得
                    int index = 0;
                    if (mappedR == 0) index = mappedC - 1;       // (0,1)->0, (0,2)->1
                    else if (mappedR == 1) index = 1 + mappedC;  // (1,1)->2, (1,2)->3
                    else if (mappedR == 2) index = 4;            // (2,2)->4

                    weights[r * 6 + c] = base[index];
                }
            }
        }
    }

    public float value(Board board) {
        if (board.isEnd()) {
            return 1_000_000 * board.score();
        }

        float score = 0;
        for (int k = 0; k < LENGTH; k++) {
            Color c = board.get(k);
            if (c == Color.BLACK || c == Color.WHITE) {
                score += weights[k] * c.getValue();
            }
        }
        return score;
    }
}