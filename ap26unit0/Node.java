import java.util.*;

/**
 * 教材用のゲーム木（深さ 3）。
 *
 * <pre>
 *               A           ← max の手番（根）
 *              / \
 *             B   C         ← min の手番
 *            /|   |\
 *           D E   F G       ← 葉。末端評価値だけが意味を持つ
 *          3.0 2.0 1.0 4.0
 * </pre>
 *
 * 期待結果: max( min(3.0, 2.0), min(1.0, 4.0) ) = max(2.0, 1.0) = 2.0
 *
 * <h2>unit1 への橋渡し</h2>
 * このクラスは、unit1 で扱うオセロの世界を最小限まで簡略化した抽象例である。
 * <pre>
 *   unit0 の概念         ↔  unit1 (オセロ) の概念
 *   ─────────────────────────────────────────────
 *   Node                ↔  ap26.Board       (世界の状態)
 *   Move                ↔  ap26.Move        (状態遷移の指示)
 *   node.perform(move)  ↔  board.placed(move)        (状態遷移を適用)
 *   node.getMoves()     ↔  board.findLegalMoves(c)   (合法手の列挙)
 *   node.isGoal()       ↔  board.isEnd()             (終局判定)
 *   Eval.value(node)    ↔  MyEval.value(board)       (末端評価)
 * </pre>
 * ゲーム木探索の本質はこの 6 要素にあり、オセロ・チェス・将棋など多くの
 * 二人完全情報ゲームが、この同じ枠組みで実装できる。本ファイルはその
 * 「枠組みそのもの」を最小例で示すための教材である。
 */
class Node {

  /**
   * 葉ノードの評価値（max プレイヤー視点）。
   * 葉でないノードは EVAL に登場しない。
   */
  static final Map<String, Float> EVAL = Map.of(
      "D", 3.0f,
      "E", 2.0f,
      "F", 1.0f,
      "G", 4.0f);

  /**
   * 各ノードから到達できる子ノード。葉は CHILDREN に登場しない
   * （= 子を持たない = 葉、として {@link #isGoal()} で判定する）。
   */
  static final Map<String, List<String>> CHILDREN = Map.of(
      "A", List.of("B", "C"),
      "B", List.of("D", "E"),
      "C", List.of("F", "G"));

  /** このノードを示すラベル（"A"〜"G"）。*/
  final String name;

  Node(String name) {
    this.name = name;
  }

  /**
   * 指定された手を適用して得られる新しい状態を返す。
   * <p>unit1 では {@code board.placed(move)} に対応する。
   * unit1 ではオセロのルールに従って石を反転させた新しい盤面を返すが、
   * この簡略例では「行き先のノードを生成する」だけで済む。
   */
  Node perform(Move move) {
    return new Node(move.name);
  }

  /**
   * このノードから指せる手のリスト。葉なら空リスト。
   * <p>unit1 では {@code board.findLegalMoves(color)} に対応する。
   * 戻り値は新しい List で、Node 自身の状態は変化しない。
   */
  List<Move> getMoves() {
    List<Move> result = new ArrayList<>();
    for (String childName : CHILDREN.getOrDefault(this.name, List.of())) {
      result.add(new Move(childName));
    }
    return result;
  }

  /**
   * 終局（葉）か。
   * <p>unit1 では {@code board.isEnd()} に対応する。
   * この簡略例では「子が無い = 終局」だが、unit1 オセロでは
   * 「双方とも合法手がない」など、別の条件で終局を判定する。
   */
  boolean isGoal() {
    return getMoves().isEmpty();
  }

  @Override
  public String toString() {
    return this.name;
  }
}

/**
 * 状態遷移の指示。
 * <p>unit1 の {@link ap26.Move} に対応する抽象。unit0 では「行き先のノード名」
 * だけを持つ最小実装だが、unit1 ではマス位置 {@code index} と石の色 {@code color}
 * を持つ。
 *
 * <p>状態 (Node) と状態遷移 (Move) を別クラスに分けるのは、ゲーム木探索の
 * 基本構造で「世界の状態」と「世界を変える指示」を分離するためである。
 */
class Move {
  /** 行き先のノード名。unit1 では index + color に相当する。*/
  final String name;

  Move(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return this.name;
  }
}

/**
 * ノードの評価関数。
 * <p>unit1 の {@code MyEval} に対応する抽象。unit0 では事前に決めた葉の値を
 * 返すだけだが、unit1 では盤面の各マスに重みをかけて合計するなど、より
 * 複雑な計算を行う。
 *
 * <p>評価関数自体はインスタンス間で状態を持たないため、{@code shared} という
 * 単一インスタンスを使い回す（シングルトン）。これにより各探索ノードで
 * {@code new Eval()} する無駄を省ける。
 */
class Eval {
  /** 全プレイヤーで共有する評価器。状態を持たないので安全に共有できる。*/
  static final Eval shared = new Eval();

  /** ノードに対する評価値（max プレイヤー視点）。葉のみ意味を持つ。*/
  float value(Node node) {
    return Node.EVAL.get(node.name);
  }
}
