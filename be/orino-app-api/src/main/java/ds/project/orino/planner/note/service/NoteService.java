package ds.project.orino.planner.note.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.note.entity.Note;
import ds.project.orino.domain.planner.note.repository.NoteRepository;
import ds.project.orino.planner.note.dto.NoteCreateRequest;
import ds.project.orino.planner.note.dto.NoteDetailResponse;
import ds.project.orino.planner.note.dto.NoteTreeNode;
import ds.project.orino.planner.note.dto.NoteTreeResponse;
import ds.project.orino.planner.note.dto.NoteUpdateRequest;
import ds.project.orino.planner.note.dto.NoteUpdateResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class NoteService {

    static final int MAX_CONTENT_BYTES = 1024 * 1024;
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final NoteRepository noteRepository;
    private final StudyMaterialRepository studyMaterialRepository;

    public NoteService(NoteRepository noteRepository,
                       StudyMaterialRepository studyMaterialRepository) {
        this.noteRepository = noteRepository;
        this.studyMaterialRepository = studyMaterialRepository;
    }

    public NoteTreeResponse findTree(Long memberId, Long materialId) {
        requireOwnedMaterial(memberId, materialId);
        List<Note> notes = noteRepository.findAllByMaterialIdOrderBySortOrderAscIdAsc(materialId);
        return new NoteTreeResponse(buildTree(notes));
    }

    public NoteDetailResponse findOne(Long memberId, Long noteId) {
        return NoteDetailResponse.of(getOwnedNote(memberId, noteId));
    }

    @Transactional
    public NoteDetailResponse create(Long memberId, Long materialId, NoteCreateRequest request) {
        requireOwnedMaterial(memberId, materialId);

        Long parentId = request.parentId();
        if (parentId != null) {
            Note parent = getOwnedNote(memberId, parentId);
            if (!parent.getMaterialId().equals(materialId)) {
                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }
        }

        int sortOrder = noteRepository.findMaxSortOrder(materialId, parentId) + 1;
        Note saved = noteRepository.save(
                new Note(memberId, materialId, parentId, request.title(), sortOrder));
        return NoteDetailResponse.of(saved);
    }

    @Transactional
    public NoteUpdateResponse update(Long memberId, Long noteId, NoteUpdateRequest request) {
        if (request.title() == null && request.content() == null
                && request.parentId() == null && request.sortOrder() == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        Note note = getOwnedNote(memberId, noteId);

        if (request.content() != null) {
            String serialized = serialize(request.content());
            if (serialized.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }
            note.updateContent(serialized);
        }
        if (request.title() != null) {
            note.updateTitle(request.title());
        }
        if (request.parentId() != null) {
            moveTo(memberId, note, request.parentId());
        }
        if (request.sortOrder() != null) {
            note.updateSortOrder(request.sortOrder());
        }

        return NoteUpdateResponse.of(note);
    }

    @Transactional
    public void delete(Long memberId, Long noteId) {
        Note note = getOwnedNote(memberId, noteId);
        noteRepository.delete(note);
    }

    private void moveTo(Long memberId, Note note, Long newParentId) {
        if (newParentId.equals(note.getId())) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        Note newParent = getOwnedNote(memberId, newParentId);
        if (!newParent.getMaterialId().equals(note.getMaterialId())) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        if (isDescendant(note.getMaterialId(), note.getId(), newParentId)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        note.updateParent(newParentId);
    }

    /**
     * candidateId가 rootId의 서브트리(자손) 안에 있으면 true.
     */
    private boolean isDescendant(Long materialId, Long rootId, Long candidateId) {
        List<Note> all = noteRepository.findAllByMaterialIdOrderBySortOrderAscIdAsc(materialId);
        Map<Long, List<Long>> childrenByParent = new HashMap<>();
        for (Note n : all) {
            childrenByParent
                    .computeIfAbsent(n.getParentId(), k -> new ArrayList<>())
                    .add(n.getId());
        }
        Set<Long> visited = new HashSet<>();
        List<Long> stack = new ArrayList<>(childrenByParent.getOrDefault(rootId, List.of()));
        while (!stack.isEmpty()) {
            Long cur = stack.remove(stack.size() - 1);
            if (!visited.add(cur)) {
                continue;
            }
            if (cur.equals(candidateId)) {
                return true;
            }
            stack.addAll(childrenByParent.getOrDefault(cur, List.of()));
        }
        return false;
    }

    private List<NoteTreeNode> buildTree(List<Note> notes) {
        Map<Long, List<Note>> childrenByParent = new HashMap<>();
        for (Note n : notes) {
            childrenByParent
                    .computeIfAbsent(n.getParentId(), k -> new ArrayList<>())
                    .add(n);
        }
        return toNodes(childrenByParent.get(null), childrenByParent);
    }

    private List<NoteTreeNode> toNodes(List<Note> level, Map<Long, List<Note>> childrenByParent) {
        if (level == null) {
            return List.of();
        }
        List<NoteTreeNode> nodes = new ArrayList<>(level.size());
        for (Note n : level) {
            nodes.add(new NoteTreeNode(
                    n.getId(), n.getTitle(), n.getParentId(), n.getSortOrder(),
                    toNodes(childrenByParent.get(n.getId()), childrenByParent)));
        }
        return nodes;
    }

    private void requireOwnedMaterial(Long memberId, Long materialId) {
        studyMaterialRepository.findByIdAndMemberId(materialId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Note getOwnedNote(Long memberId, Long noteId) {
        return noteRepository.findByIdAndMemberId(noteId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static String serialize(JsonNode content) {
        try {
            return MAPPER.writeValueAsString(content);
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, e);
        }
    }
}
