package ds.project.orino.planner.lifelog.flow.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 흐름에 기록 담기. 단건({@code momentId}) 또는 다건({@code momentIds}) 어느 쪽으로도 보낼 수 있다.
 * 서버가 둘을 합쳐 처리하며, 이미 담긴 기록·소유가 아닌 기록은 무시한다(멱등).
 */
public record AddMomentsRequest(
        Long momentId,
        List<Long> momentIds
) {

    /** 단건·다건을 하나로 합친다. */
    public List<Long> allMomentIds() {
        List<Long> ids = new ArrayList<>();
        if (momentId != null) {
            ids.add(momentId);
        }
        if (momentIds != null) {
            ids.addAll(momentIds);
        }
        return ids;
    }
}
