package ds.project.orino.domain.planner.dataset.entity;

/**
 * 수식 참조의 종류. 셋을 구분해야 수식을 아래로 복사할 수 있다 —
 * 모든 참조를 행 id에 묶으면 5행의 {@code =c0*c1}을 6행에 복사했을 때
 * 6행이 5행 값을 참조하게 된다. 엑셀의 상대/절대 참조와 같은 구분.
 */
public enum FormulaRefKind {

    /**
     * 같은 행의 그 열({@code =c0*c1}). <b>행 id를 쓰지 않는다</b> — 각 행이 자기 행을 가리키므로
     * 복사가 그대로 동작하고, 무효화 전파도 같은 행 안으로 좁혀진다. 계산 열이 여기 해당한다.
     */
    SAME_ROW,

    /** 특정 행의 그 열({@code =B5}). 행 id에 묶여 삽입·삭제로 순번이 밀려도 같은 행을 가리킨다. */
    ABSOLUTE,

    /** 열 전체({@code =SUM(c2)}). 그 열의 아무 행이나 바뀌면 재계산 대상. */
    COLUMN_ALL
}
