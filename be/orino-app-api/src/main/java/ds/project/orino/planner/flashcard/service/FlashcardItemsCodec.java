package ds.project.orino.planner.flashcard.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.planner.flashcard.dto.OrderingItem;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 순서 카드 항목(List&lt;OrderingItem&gt;) ↔ JSON 문자열 직렬화. 저장 시 항상 정답 순서를 유지한다.
 * ({@code note.content}와 동일하게 원문 JSON 문자열을 엔티티에 보관하는 패턴)
 */
@Component
public class FlashcardItemsCodec {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<OrderingItem>> LIST_TYPE = new TypeReference<>() {
    };

    /** 항목 리스트를 JSON 문자열로 직렬화한다. null이면 null(=BASIC 카드). */
    public String serialize(List<OrderingItem> items) {
        if (items == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(items);
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, e);
        }
    }

    /** 저장된 JSON 문자열을 항목 리스트로 역직렬화한다. null이면 null(=BASIC 카드). */
    public List<OrderingItem> parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, LIST_TYPE);
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }
}
