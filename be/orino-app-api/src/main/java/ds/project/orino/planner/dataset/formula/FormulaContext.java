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

    /**
     * 표 이름 → 대상 표 id. 표간 참조({@code {요약!환율}1})의 표 이름을 해석한다. 이름은 노트
     * 안에서만 유일하므로 FE가 준 {@code tableRefs} 맵으로 푼다. 없거나 접근 불가면 empty.
     * 표간 참조를 지원하지 않는 컨텍스트(테스트 등)는 기본 empty.
     */
    default Optional<Long> tableIdByName(String name) {
        return Optional.empty();
    }

    /** 대상 표 id → 이름. 표간 참조 표시용. 지워졌거나 무명이면 empty. */
    default Optional<String> tableNameById(long datasetId) {
        return Optional.empty();
    }

    /**
     * 대상 표의 열·행을 보는 컨텍스트. 표간 참조는 열 label·행 번호를 <b>대상 표</b> 기준으로
     * 해석해야 한다. 기본은 자기 자신(표간 미지원 컨텍스트는 같은 표로 본다).
     */
    default FormulaContext forDataset(long datasetId) {
        return this;
    }
}
