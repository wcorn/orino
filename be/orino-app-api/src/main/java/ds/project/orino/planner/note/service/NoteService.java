package ds.project.orino.planner.note.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.note.entity.Note;
import ds.project.orino.domain.planner.note.repository.NoteRepository;
import ds.project.orino.planner.note.dto.NoteResponse;
import ds.project.orino.planner.note.dto.NoteUpdateResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

@Service
@Transactional(readOnly = true)
public class NoteService {

    static final int MAX_CONTENT_BYTES = 1024 * 1024;
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final NoteRepository noteRepository;

    public NoteService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    public NoteResponse findByMaterialId(Long memberId, Long materialId) {
        return NoteResponse.of(getOwnedNote(memberId, materialId));
    }

    @Transactional
    public NoteUpdateResponse update(Long memberId, Long materialId, JsonNode content) {
        String serialized = serialize(content);
        if (serialized.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        Note note = getOwnedNote(memberId, materialId);
        note.updateContent(serialized);
        return NoteUpdateResponse.of(note);
    }

    private Note getOwnedNote(Long memberId, Long materialId) {
        Note note = noteRepository.findByMaterialId(materialId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!note.getMemberId().equals(memberId)) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return note;
    }

    private static String serialize(JsonNode content) {
        try {
            return MAPPER.writeValueAsString(content);
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, e);
        }
    }
}
