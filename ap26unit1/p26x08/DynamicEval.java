package p26x08;

import ap26.Board;
import static ap26.Board.LENGTH;
import static ap26.Board.SIZE;
import static ap26.Color.*;
import java.util.stream.IntStream;

/**
 * オセロ盤面の評価関数。
 *
 * <p>unit0 の {@link Eval} に対応する具象クラス。unit0 では葉ノードに事前に
 * 値を割り当てるだけだったが、unit1 では「探索の打ち切り時点での盤面の
 * 優劣を数値化する」必要があり、その実装の中核となる。
 *
 * <h2>評価戦略: マス重み法</h2>
 * 6×6 オセロでは「どのマスに自分の石があると有利か」を経験的に決められる。
 * 本クラスでは {@link #M} の重み行列をマスごとに掛けて合計する単純な方式
 * を採用する。
 *
 * <pre>
 *  重み行列 M（黒視点）:
 *     a   b   c   d   e   f
 *  1| 10  10  10  10  10  10|   ← 辺（角・端）は強い
 *  2| 10  -5   1   1  -5  10|   ← b2 / e2 は危険（角を相手に渡しやすい）
 *  3| 10   1   1   1   1  10|
 *  4| 10   1   1   1   1  10|
 *  5| 10  -5   1   1  -5  10|
 *  6| 10  10  10  10  10  10|
 * </pre>
 *
 * <p>角周辺 (b2, e2, b5, e5) を負にしているのは、これらに早く石を置くと
 * 角を相手に与えやすくなるという経験則による。本格的な評価関数では
 * 「安定石（絶対に取られない石）の数」や「機動力（相手の合法手数）」を
 * 組み合わせるが、本教材では単純な重み和に留めている。
 *
 * <h2>終局時の特別扱い</h2>
 * 盤面が終局していたら、{@link Board#score()}（黒石数 − 白石数）に
 * 大きな係数 {@code 1_000_000} を掛けて返す。これは
 * <ul>
 *   <li>「確実に勝てる手順」は他のどんな中間評価よりも優先される</li>
 *   <li>α-β 探索の途中で「終局までの読み筋」が見つかれば即決できる</li>
 * </ul>
 * という効果を持つ。係数は「実際の評価値レンジを越える十分大きな値」で
 * あれば何でもよく、慣例的に 10^6 が使われる。
 */
public class DynamicEval {

  /**
   * マスごとの重み行列。M[row][col] でアクセスする。
   * 角・辺は大きな正の値、角の斜め隣 (X マス) は負の値。
   */
  static final float[][][] M = {
    {
      { 50, -5, 3, 3, -5, 50 },
      { -5, -10,  10,  10, -10, -5 },
      { 3,  10,  1,  1,  10, 3 },
      { 3,  10,  1,  1,  10, 3 },
      { -5, -10,  10,  10, -10, -5 },
      { 50, -5, 3, 3, -5, 50 },
    },
    {
      { 100, -5, 5, 5, -5, 100 },
      { -5, -10,  10,  10, -10, -5 },
      { 5,  10,  1,  1,  10, 5 },
      { 5,  10,  1,  1,  10, 5 },
      { -5, -10,  10,  10, -10, -5 },
      { 100, -5, 5, 5, -5, 100 },
    },
    {
      { 50, 5, 10, 10, 5, 50 },
      { 5, -5,  1,  1, -5, 5 },
      { 10,  1,  1,  1,  1, 10 },
      { 10,  1,  1,  1,  1, 10 },
      { 5, -5,  1,  1, -5, 5 },
      { 50, 5, 10, 10, 5, 50 },
    }
  };
  float [][][] MC = new float[3][6][6];

  public void InitializeEval(Board board){
    for(int i=0;i<3;i++){
      for(int j=0;j<6;j++){
        for(int k=0;k<6;k++) MC[i][j][k] = M[i][j][k];
      }
    }
    for(int i=0;i<3;i++){
      blockArrange(board, MC[i]);
    }
  }

  static final float[][] N = {
    {50,-16.2f,19.72f,19.72f,-16.2f,50},
    {-16.2f,-33.86f,-7.6f,-7.6f,-33.86f,-16.2f},
    {19.72f,-7.6f,24.51f,24.51f,-7.6f,19.72f},
    {19.72f,-7.6f,24.51f,24.51f,-7.6f,19.72f},
    {-16.2f,-33.86f,-7.6f,-7.6f,-33.86f,-16.2f},
    {50,-16.2f,19.72f,19.72f,-16.2f,50},
  };

  /**
   * 左から順に評価関数、先手の合法手、後手の合法手、先手の石の数、後手の石の数に掛ける重み
   * 上から順に序盤手、中盤手、終盤手
   */
  static final float[][] W = {
    {1,9,-9,-3,3,-6,6},
    {1,9,-9,3,-3,-6,6},
    {1,6,-10,8,-5,0,0}
  };
  static final float[][] V = {
    {0.08f, 10.96f, -7.12f, -0.81f, 3.58f, -9.87f, 3.12f, },
    {0.87f, 15.44f, -7.61f, -0.98f, -2.69f, -4.52f, 8.33f, },
    {1.51f, 6.15f, -10.23f, 14.61f, -5.00f, 0.12f, -3.51f, },
  };

  /**
   * 序盤、中盤、終盤の境界
   */
  final int firstLine = 20;
  final int secondLine = 28;

  /**
   * 盤面の評価値を返す（黒視点）。正なら黒有利、負なら白有利。
   *
   * @param board 評価対象の盤面
   * @return 評価値。終局時は ±1,000,000 ×（黒石数−白石数）。
   *         非終局時はマス重みの線形和。
   */
  
  public float value(Board board) {
    if (board.isEnd()) {
      // 確実に勝てる手順を最優先するため、大きな係数で増幅。
      return 1_000_000 * board.score();
    }

    int part;
    int countHands = board.count(BLACK)+board.count(WHITE)+board.count(BLOCK);
    if(countHands<firstLine) part=0;
    else if(countHands>=secondLine) part=2;
    else part=1;
    
    // 確定石1つにつき 50.0 という圧倒的な価値を持たせる
    float stableBonus = countStableDisks(board, BLACK) * 50.0f 
                      - countStableDisks(board, WHITE) * 50.0f;
    
    return (float) IntStream.range(0, LENGTH)
        .mapToDouble(k -> cellScore(board, k, part))
        .sum() * V[part][0]
        + board.findLegalMoves(BLACK).size() * V[part][1]
        + board.findLegalMoves(WHITE).size() * V[part][2]
        + board.count(BLACK) * V[part][3]
        + board.count(WHITE) * V[part][4]
        + relationValue.countFrontier(board,BLACK) * V[part][5]
        + relationValue.countFrontier(board,WHITE) * V[part][6]
        + stableBonus;
  }

  /** 1 マス分の評価値。黒石なら +M[r][c]、白石なら -M[r][c]、空マスは 0。*/
  float cellScore(Board board, int k, int part) {
    int row = k / SIZE;
    int col = k % SIZE;
    return MC[1][row][col] * board.get(k).getValue();
    //return N[row][col] * board.get(k).getValue();
  }

  public static void blockArrange(Board board, float MC[][]){
    for(int k=0;k<6;k++){
      if(board.get(k)==BLOCK){
        if(k==0){MC[0][1]+=40;MC[1][0]+=40;}
        else if(k==5){MC[0][4]+=40;MC[1][5]+=40;}
        else{MC[0][k-1]+=40;MC[0][k+1]+=40;MC[1][k]+=10;}
      }
    }
    for(int k=6;k<Board.LENGTH;k+=6){
      if(board.get(k)==BLOCK){
        if(k==30){MC[4][0]+=40;MC[5][1]+=40;}
        else{MC[k/6-1][0]+=40;MC[k/6+1][0]+=40;MC[k/6][1]+=10;}
      }
    }
  }
  
  public int countStableDisks(Board board, ap26.Color color) {
      boolean[] isStable = new boolean[ap26.Board.LENGTH];
      int stableCount = 0;

      // --- 上辺 (0 〜 5) ---
      if (board.get(0) == color) {
          for (int i = 0; i <= 5; i++) {
              if (board.get(i) == color) isStable[i] = true;
              else break;
          }
      }
      if (board.get(5) == color) {
          for (int i = 5; i >= 0; i--) {
              if (board.get(i) == color) isStable[i] = true;
              else break;
          }
      }

      // --- 下辺 (30 〜 35) ---
      if (board.get(30) == color) {
          for (int i = 30; i <= 35; i++) {
              if (board.get(i) == color) isStable[i] = true;
              else break;
          }
      }
      if (board.get(35) == color) {
          for (int i = 35; i >= 30; i--) {
              if (board.get(i) == color) isStable[i] = true;
              else break;
          }
      }

      // --- 左辺 (0, 6, 12, 18, 24, 30) ---
      if (board.get(0) == color) {
          for (int i = 0; i <= 30; i += 6) {
              if (board.get(i) == color) isStable[i] = true;
              else break;
          }
      }
      if (board.get(30) == color) {
          for (int i = 30; i >= 0; i -= 6) {
              if (board.get(i) == color) isStable[i] = true;
              else break;
          }
      }

      // --- 右辺 (5, 11, 17, 23, 29, 35) ---
      if (board.get(5) == color) {
          for (int i = 5; i <= 35; i += 6) {
              if (board.get(i) == color) isStable[i] = true;
              else break;
          }
      }
      if (board.get(35) == color) {
          for (int i = 35; i >= 5; i -= 6) {
              if (board.get(i) == color) isStable[i] = true;
              else break;
          }
      }

      // 確定石の数を集計
      for (boolean b : isStable) {
          if (b) stableCount++;
      }
      return stableCount;
  }
}
