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
      { 100, -3, 5, 5, -3, 100 },
      { -3, -10,  10,  10, -10, -3 },
      { 5,  10,  1,  1,  10, 5 },
      { 5,  10,  1,  1,  10, 5 },
      { -3, -10,  10,  10, -10, -3 },
      { 100, -3, 5, 5, -3, 100 },
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
    {1,4,-2,-1,3},
    {1,3,-4,1,1},
    {1,4,-2,8,-5}
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
    int countHands = board.count(BLACK)+board.count(WHITE);
    if(countHands<firstLine) part=0;
    else if(countHands>=secondLine) part=2;
    else part=1;
    return (float) IntStream.range(0, LENGTH)
        .mapToDouble(k -> cellScore(board, k, part))
        .sum() * W[part][0]
        + board.findLegalMoves(BLACK).size() * W[part][1]
        + board.findLegalMoves(WHITE).size() * W[part][2]
        + board.count(BLACK) * W[part][3]
        + board.count(WHITE) * W[part][4];
  }

  /** 1 マス分の評価値。黒石なら +M[r][c]、白石なら -M[r][c]、空マスは 0。*/
  float cellScore(Board board, int k, int part) {
    int row = k / SIZE;
    int col = k % SIZE;
    //return M[1][row][col] * board.get(k).getValue();
    return N[row][col] * board.get(k).getValue();
  }
}
