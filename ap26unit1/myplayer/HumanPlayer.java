package myplayer;

import java.util.List;
import java.util.Scanner;

import ap26.Board;
import ap26.Color;
import ap26.Move;
import ap26.Player;

/**
 * 標準入力から人間の手を受け取るプレイヤークラス。
 */
public class HumanPlayer extends Player {

  // 標準入力を受け取るためのスキャナ
  private Scanner scanner;

  public HumanPlayer(Color color) {
    super("Human", color);
    this.scanner = new Scanner(System.in);
  }

  @Override
  public Move think(Board board) {
    List<Move> legalMoves = board.findLegalMoves(getColor());

    // どこにも石を置けない（合法手がパスのみの）場合は、入力を求めず自動でパスする
    if (legalMoves.size() == 1 && legalMoves.get(0).isPass()) {
      System.out.println("置ける場所がありません。自動的にパスします。");
      return legalMoves.get(0);
    }

    // 正しい手が入力されるまでループ
    while (true) {
      System.out.printf("\n%s の番です。手を入力してください (例: c3) [合法手: %s]: ", getColor(), legalMoves);
      String input = scanner.nextLine().trim().toLowerCase();

      try {
        Move move;
        // パスの明示的入力（通常は自動パス処理に入りますが、念のためのフォールバック）
        if (input.equals("pass") || input.equals("..")) {
          move = Move.ofPass(getColor());
        }
        // 通常の座標入力 (例: "c3")
        else if (input.length() == 2) {
          move = Move.of(input, getColor());
        }
        // 桁数がおかしい場合
        else {
          System.err.println("※入力フォーマットが不正です。'c3' のように2文字で入力してください。");
          continue;
        }

        // 入力された手が現在の盤面の合法手リストに含まれているか検証
        if (legalMoves.contains(move)) {
          return move; // 正しい手であれば確定して返す
        } else {
          System.err.println("※その手は打てません。表示されている合法手の中から選んでください。");
        }

      } catch (Exception e) {
        // "zz" のようなパース不能な文字列が来た場合の例外キャッチ
        System.err.println("※入力エラーです。正しい座標を指定してください。");
      }
    }
  }
}