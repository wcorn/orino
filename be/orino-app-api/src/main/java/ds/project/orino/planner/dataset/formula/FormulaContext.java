package ds.project.orino.planner.dataset.formula;

import java.util.List;
import java.util.Optional;

/**
 * 파서가 바깥 세계를 보는 창. 열 목록과 행 번호↔id 변환만 제공한다 —
 * 파서 자체는 DB를 모른다.
 */
public interface FormulaContext {

    /** 현재 열 key 목록(표시 순서). 범위 스냅샷이 이 순서를 기준으로 펼쳐진다. */
    List<String> columnKeys();

    /** label → key. 없으면 empty. 중복 label은 참조를 지목할 수 없어 예외다. */
    Optional<String> keyByLabel(String label);

    /** key → label. 표시 문자열을 만들 때. */
    Optional<String> labelByKey(String key);

    /** 화면의 행 번호(1-base) → 행 id. 파싱 시점에 한 번만 해석한다. */
    Optional<Long> rowIdByNumber(int rowNumber);

    /** 행 id → 현재 행 번호(1-base). 표시할 때. 지워진 행이면 empty. */
    Optional<Integer> rowNumberById(long rowId);
}
