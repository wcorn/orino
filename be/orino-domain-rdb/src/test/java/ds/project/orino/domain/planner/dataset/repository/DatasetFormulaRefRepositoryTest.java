package ds.project.orino.domain.planner.dataset.repository;

import ds.project.orino.domain.member.entity.Member;
import ds.project.orino.domain.member.repository.MemberRepository;
import ds.project.orino.domain.planner.dataset.entity.Dataset;
import ds.project.orino.domain.planner.dataset.entity.DatasetFormula;
import ds.project.orino.domain.planner.dataset.entity.DatasetFormulaRef;
import ds.project.orino.domain.planner.dataset.entity.DatasetRow;
import ds.project.orino.domain.support.RepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 무효화 역방향 조회 검증. "이 셀이 바뀌면 무엇을 다시 계산할지"가 수식 엔진의 뼈대라
 * 참조 3종이 각각 제대로 걸리는지, 그리고 <b>안 걸려야 할 때 안 걸리는지</b>를 고정한다.
 *
 * <p>FK cascade는 여기서 검증하지 않는다. 이 모듈의 테스트는 {@code ddl-auto: create-drop}으로
 * 엔티티에서 스키마를 만드는데, dataset 엔티티들은 FK를 {@code @ManyToOne}이 아닌 Long 컬럼으로
 * 두므로 Hibernate가 FK를 만들지 않는다. cascade는 Liquibase 스키마의 몫이라
 * 실제 changeSet이 적용되는 곳(app-api 통합 테스트·로컬 확인)에서 본다.
 */
@RepositoryTest
@Transactional
class DatasetFormulaRefRepositoryTest {

    @Autowired
    private DatasetFormulaRepository formulaRepository;
    @Autowired
    private DatasetFormulaRefRepository refRepository;
    @Autowired
    private DatasetRepository datasetRepository;
    @Autowired
    private DatasetRowRepository rowRepository;
    @Autowired
    private MemberRepository memberRepository;

    private Long datasetId;
    private Long row1;
    private Long row2;

    @BeforeEach
    void setUp() {
        Long memberId = memberRepository.save(new Member("formulauser", "pw")).getId();
        datasetId = datasetRepository.save(new Dataset(memberId,
                "[{\"key\":\"c0\",\"label\":\"과목\"},{\"key\":\"c1\",\"label\":\"점수\"},"
                        + "{\"key\":\"c2\",\"label\":\"합계\"}]", 3)).getId();
        row1 = rowRepository.save(new DatasetRow(datasetId, 0, "{\"c0\":\"a\",\"c1\":\"1\"}")).getId();
        row2 = rowRepository.save(new DatasetRow(datasetId, 1, "{\"c0\":\"b\",\"c1\":\"2\"}")).getId();
    }

    /** rowId 행의 colKey 셀에 수식을 하나 만들고 id를 반환. */
    private Long formula(Long rowId, String colKey, String raw) {
        return formulaRepository.save(new DatasetFormula(datasetId, rowId, colKey, raw)).getId();
    }

    @Test
    @DisplayName("SAME_ROW 참조는 수식이 있는 그 행이 바뀔 때만 걸린다")
    void sameRow_isScopedToItsOwnRow() {
        // 두 행 모두 c2 = c1을 참조하는 계산 열.
        Long f1 = formula(row1, "c2", "=c1");
        Long f2 = formula(row2, "c2", "=c1");
        refRepository.save(DatasetFormulaRef.sameRow(f1, datasetId, "c1"));
        refRepository.save(DatasetFormulaRef.sameRow(f2, datasetId, "c1"));

        // 1행의 c1이 바뀌면 1행 수식만 재계산 대상이어야 한다.
        assertThat(refRepository.findDependentFormulaIds(datasetId, row1, "c1"))
                .containsExactly(f1);
        assertThat(refRepository.findDependentFormulaIds(datasetId, row2, "c1"))
                .containsExactly(f2);
    }

    @Test
    @DisplayName("ABSOLUTE 참조는 가리키는 그 셀이 바뀔 때만 걸린다")
    void absolute_matchesOnlyTargetCell() {
        // 2행의 c2가 1행의 c1을 콕 집어 참조.
        Long f = formula(row2, "c2", "=c1@row1");
        refRepository.save(DatasetFormulaRef.absolute(f, datasetId, row1, "c1"));

        assertThat(refRepository.findDependentFormulaIds(datasetId, row1, "c1"))
                .containsExactly(f);
        // 2행의 c1이 바뀌어도 이 수식은 무관하다.
        assertThat(refRepository.findDependentFormulaIds(datasetId, row2, "c1")).isEmpty();
        // 다른 열이 바뀌어도 무관.
        assertThat(refRepository.findDependentFormulaIds(datasetId, row1, "c0")).isEmpty();
    }

    @Test
    @DisplayName("COLUMN_ALL 참조는 그 열의 어느 행이 바뀌든 걸린다")
    void columnAll_matchesAnyRowInColumn() {
        Long f = formula(row1, "c2", "=SUM(c1)");
        refRepository.save(DatasetFormulaRef.columnAll(f, datasetId, "c1"));

        assertThat(refRepository.findDependentFormulaIds(datasetId, row1, "c1"))
                .containsExactly(f);
        assertThat(refRepository.findDependentFormulaIds(datasetId, row2, "c1"))
                .containsExactly(f);
        // 다른 열은 무관.
        assertThat(refRepository.findDependentFormulaIds(datasetId, row1, "c0")).isEmpty();
    }

    @Test
    @DisplayName("역방향 조회는 dataset 경계를 넘지 않는다")
    void doesNotLeakAcrossDatasets() {
        Long other = memberRepository.save(new Member("other", "pw")).getId();
        Long otherDataset = datasetRepository.save(
                new Dataset(other, "[{\"key\":\"c0\",\"label\":\"x\"}]", 1)).getId();
        Long otherRow = rowRepository.save(
                new DatasetRow(otherDataset, 0, "{\"c0\":\"z\"}")).getId();
        Long otherFormula = formulaRepository.save(
                new DatasetFormula(otherDataset, otherRow, "c0", "=1")).getId();
        refRepository.save(DatasetFormulaRef.columnAll(otherFormula, otherDataset, "c1"));

        // 같은 col_key라도 다른 dataset의 수식은 안 걸린다.
        assertThat(refRepository.findDependentFormulaIds(datasetId, row1, "c1")).isEmpty();
    }

    @Test
    @DisplayName("열을 참조하는 수식은 종류 불문 찾는다(열 삭제 시 #REF! 대상)")
    void findFormulasReferencingColumn() {
        Long f1 = formula(row1, "c2", "=c1");
        Long f2 = formula(row2, "c2", "=SUM(c1)");
        refRepository.save(DatasetFormulaRef.sameRow(f1, datasetId, "c1"));
        refRepository.save(DatasetFormulaRef.columnAll(f2, datasetId, "c1"));

        assertThat(refRepository.findFormulaIdsReferencingColumn(datasetId, "c1"))
                .containsExactlyInAnyOrder(f1, f2);
        assertThat(refRepository.findFormulaIdsReferencingColumn(datasetId, "c0")).isEmpty();
    }

    @Test
    @DisplayName("행을 콕 집어 참조하던 수식만 찾는다(행 삭제 시 #REF! 대상)")
    void findFormulasReferencingRow() {
        Long absolute = formula(row2, "c2", "=c1@row1");
        Long sameRow = formula(row1, "c2", "=c1");
        refRepository.save(DatasetFormulaRef.absolute(absolute, datasetId, row1, "c1"));
        refRepository.save(DatasetFormulaRef.sameRow(sameRow, datasetId, "c1"));

        // SAME_ROW는 행이 지워지면 수식도 함께 사라지므로 #REF! 대상이 아니다.
        assertThat(refRepository.findFormulaIdsReferencingRow(datasetId, row1))
                .containsExactly(absolute);
    }

    @Test
    @DisplayName("수식이 지워지면 참조도 함께 지워진다")
    void deletingFormulaRemovesRefs() {
        Long f = formula(row1, "c2", "=c1");
        refRepository.save(DatasetFormulaRef.sameRow(f, datasetId, "c1"));

        refRepository.deleteByFormulaId(f);
        formulaRepository.deleteById(f);

        assertThat(refRepository.findDependentFormulaIds(datasetId, row1, "c1")).isEmpty();
    }
}
