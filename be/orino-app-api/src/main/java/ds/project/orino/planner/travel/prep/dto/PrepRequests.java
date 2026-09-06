package ds.project.orino.planner.travel.prep.dto;

import ds.project.orino.domain.planner.travel.entity.PrepCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

/** 준비 API가 받는 요청들(API §10). */
public final class PrepRequests {

    private PrepRequests() {
    }

    /**
     * 항목 추가. <b>{@code title}만 필수다</b> — 붙박이 입력줄이 엔터 한 번으로 보내는
     * 요청이 이것이고, 거기서 더 요구하면 「일단 적어 둔다」가 안 된다(명세 §13).
     *
     * @param category 생략하면 {@code TODO}. 애매하면 할 일이라는 규칙을 서버도 같이 쓴다
     * @param sectionLabel 묶음 이름. 생략하거나 공백이면 묶음 없음이다(#1358)
     */
    public record Create(
            PrepCategory category,
            @NotBlank @Size(max = 100) String title,
            @Size(max = 30) String sectionLabel,
            Integer quantity,
            Integer dueDaysBefore,
            @Size(max = 500) String url,
            @Size(max = 500) String memo
    ) {
    }

    /**
     * 항목 수정. <b>보낸 것만 바꾼다</b> — 체크 토글이 {@code {"done": true}} 하나로
     * 나가는데 나머지를 「안 보냈으니 지운다」로 읽으면 제목까지 날아간다.
     *
     * <p>그래서 값을 <b>지우는</b> 것은 따로 말한다({@link #clear}). 자산 수정의
     * {@code clearGroup}과 같은 규칙인데, 준비는 지울 수 있는 칸이 여럿이라 이름을 하나씩
     * 만들지 않고 목록으로 받는다.
     *
     * <p>묶음도 그 규칙을 따른다 — 「캐리어로 옮겨 달라」와 「묶음에서 빼 달라」는 다른 일이고,
     * 후자는 {@link PrepField#SECTION_LABEL}로 적어 보낸다.
     */
    public record Patch(
            PrepCategory category,
            @Size(max = 100) String title,
            Boolean done,
            @Size(max = 30) String sectionLabel,
            Integer quantity,
            Integer dueDaysBefore,
            @Size(max = 500) String url,
            @Size(max = 500) String memo,
            Set<PrepField> clear
    ) {
    }

    /**
     * 한 분류 안의 전체 순서. <b>분류를 넘는 이동은 {@link Patch}의 {@code category}</b>이지
     * 이 요청이 아니다 — 두 분류의 순서를 한 번에 받으면 실패했을 때 어느 쪽이 반영됐는지
     * 알 수 없다.
     */
    public record Order(
            @NotNull PrepCategory category,
            @NotEmpty List<Long> itemIds
    ) {
    }
}
