package ds.project.orino.planner.ledger.importing;

import java.util.ArrayList;
import java.util.List;

/**
 * 머리글이 몇 번째 줄인지 찾는다(#1318).
 *
 * <p>1행을 머리글로 못 박으면 <b>은행 파일은 대부분 못 읽는다</b>. 카카오뱅크는 앞 10줄이
 * 제목·성명·계좌번호·주의사항이고 11행이 머리글이다. 그걸 모르면 화면의 열 이름이 전부
 * 「(이름 없음)」이 되어, 사람이 원본을 따로 열어 <b>열 번호를 세어</b> 맞춰야 한다.
 *
 * <p>찾는 방법은 <b>모양이 이어지는가</b>다. 머리글은 그 아래로 같은 칸들이 채워진 줄이
 * 죽 이어진다 — 반면 「성명 | 강동석 | 조회기간 | …」 같은 안내문은 한두 줄 뒤에 끊긴다.
 * 그래서 줄마다 「내 칸들이 아래 줄에서도 채워지는가」를 세고, 가장 많이 이어지는 줄을 고른다.
 *
 * <p><b>못 찾으면 0을 준다.</b> 머리글 없는 파일도 있고(그래서 매핑은 이름이 아니라 열 번호다),
 * 그때는 지금까지처럼 첫 줄을 머리글로 본다 — 새 규칙이 옛 파일을 깨뜨리지 않아야 한다.
 */
final class LedgerHeaderFinder {

    /** 앞쪽 이만큼만 후보로 본다. 안내문이 서른 줄을 넘는 파일은 본 적이 없다. */
    private static final int SCAN_ROWS = 30;

    /** 한 후보를 판정할 때 내다볼 줄 수. 길게 볼수록 좋지만 큰 파일에서 값이 커진다. */
    private static final int LOOKAHEAD = 20;

    /** 머리글이라면 적어도 이만큼은 채워져 있다. 날짜·금액·내용이면 셋이다. */
    private static final int MIN_CELLS = 3;

    /** 아래 줄이 「같은 모양」이라고 볼 최소 비율. 메모처럼 자주 비는 칸이 있어 1.0은 못 쓴다. */
    private static final double MATCH_RATIO = 0.7;

    private LedgerHeaderFinder() {
    }

    static int find(List<List<String>> rows) {
        int best = 0;
        int bestScore = 0;
        int limit = Math.min(rows.size(), SCAN_ROWS);

        for (int i = 0; i < limit; i++) {
            List<Integer> columns = filledColumns(rows.get(i));
            if (columns.size() < MIN_CELLS) {
                continue;
            }
            int score = 0;
            int until = Math.min(rows.size(), i + 1 + LOOKAHEAD);
            for (int j = i + 1; j < until; j++) {
                if (looksLikeData(rows.get(j), columns)) {
                    score++;
                } else {
                    // 이어지지 않으면 거기서 끝이다. 안내문은 한두 줄 뒤 끊기고,
                    // 진짜 데이터는 끊기지 않는다.
                    break;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return bestScore == 0 ? 0 : best;
    }

    private static List<Integer> filledColumns(List<String> row) {
        List<Integer> columns = new ArrayList<>();
        for (int i = 0; i < row.size(); i++) {
            String cell = row.get(i);
            if (cell != null && !cell.isBlank()) {
                columns.add(i);
            }
        }
        return columns;
    }

    /** 머리글이 쓴 칸들이 이 줄에서도 대체로 채워져 있는가. */
    private static boolean looksLikeData(List<String> row, List<Integer> columns) {
        int filled = 0;
        for (int column : columns) {
            if (column < row.size() && row.get(column) != null && !row.get(column).isBlank()) {
                filled++;
            }
        }
        return filled >= MIN_CELLS && filled >= Math.ceil(columns.size() * MATCH_RATIO);
    }
}
