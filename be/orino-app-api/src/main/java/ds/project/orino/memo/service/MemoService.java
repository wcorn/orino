package ds.project.orino.memo.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.memo.entity.Memo;
import ds.project.orino.domain.memo.repository.MemoRepository;
import ds.project.orino.memo.dto.MemoCreateRequest;
import ds.project.orino.memo.dto.MemoDetailResponse;
import ds.project.orino.memo.dto.MemoTreeNode;
import ds.project.orino.memo.dto.MemoTreeResponse;
import ds.project.orino.memo.dto.MemoUpdateRequest;
import ds.project.orino.memo.dto.MemoUpdateResponse;
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
public class MemoService {

    static final int MAX_CONTENT_BYTES = 1024 * 1024;
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final MemoRepository memoRepository;

    public MemoService(MemoRepository memoRepository) {
        this.memoRepository = memoRepository;
    }

    public MemoTreeResponse findTree(Long memberId) {
        List<Memo> memos = memoRepository.findAllByMemberIdOrderBySortOrderAscIdAsc(memberId);
        return new MemoTreeResponse(buildTree(memos));
    }

    public MemoDetailResponse findOne(Long memberId, Long memoId) {
        return MemoDetailResponse.of(getOwnedMemo(memberId, memoId));
    }

    @Transactional
    public MemoDetailResponse create(Long memberId, MemoCreateRequest request) {
        Long parentId = request.parentId();
        if (parentId != null) {
            getOwnedMemo(memberId, parentId);
        }

        int sortOrder = memoRepository.findMaxSortOrder(memberId, parentId) + 1;
        Memo saved = memoRepository.save(
                new Memo(memberId, parentId, request.title(), sortOrder));
        return MemoDetailResponse.of(saved);
    }

    @Transactional
    public MemoUpdateResponse update(Long memberId, Long memoId, MemoUpdateRequest request) {
        if (request.title() == null && request.content() == null
                && request.parentId() == null && request.sortOrder() == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        Memo memo = getOwnedMemo(memberId, memoId);

        if (request.content() != null) {
            String serialized = serialize(request.content());
            if (serialized.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }
            memo.updateContent(serialized);
        }
        if (request.title() != null) {
            memo.updateTitle(request.title());
        }
        if (request.parentId() != null) {
            moveTo(memberId, memo, request.parentId());
        }
        if (request.sortOrder() != null) {
            memo.updateSortOrder(request.sortOrder());
        }

        return MemoUpdateResponse.of(memo);
    }

    @Transactional
    public void delete(Long memberId, Long memoId) {
        Memo memo = getOwnedMemo(memberId, memoId);
        memoRepository.delete(memo);
    }

    private void moveTo(Long memberId, Memo memo, Long newParentId) {
        if (newParentId.equals(memo.getId())) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        getOwnedMemo(memberId, newParentId);
        if (isDescendant(memberId, memo.getId(), newParentId)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        memo.updateParent(newParentId);
    }

    /**
     * candidateId가 rootId의 서브트리(자손) 안에 있으면 true.
     */
    private boolean isDescendant(Long memberId, Long rootId, Long candidateId) {
        List<Memo> all = memoRepository.findAllByMemberIdOrderBySortOrderAscIdAsc(memberId);
        Map<Long, List<Long>> childrenByParent = new HashMap<>();
        for (Memo m : all) {
            childrenByParent
                    .computeIfAbsent(m.getParentId(), k -> new ArrayList<>())
                    .add(m.getId());
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

    private List<MemoTreeNode> buildTree(List<Memo> memos) {
        Map<Long, List<Memo>> childrenByParent = new HashMap<>();
        for (Memo m : memos) {
            childrenByParent
                    .computeIfAbsent(m.getParentId(), k -> new ArrayList<>())
                    .add(m);
        }
        return toNodes(childrenByParent.get(null), childrenByParent);
    }

    private List<MemoTreeNode> toNodes(List<Memo> level, Map<Long, List<Memo>> childrenByParent) {
        if (level == null) {
            return List.of();
        }
        List<MemoTreeNode> nodes = new ArrayList<>(level.size());
        for (Memo m : level) {
            nodes.add(new MemoTreeNode(
                    m.getId(), m.getTitle(), m.getParentId(), m.getSortOrder(),
                    toNodes(childrenByParent.get(m.getId()), childrenByParent)));
        }
        return nodes;
    }

    private Memo getOwnedMemo(Long memberId, Long memoId) {
        return memoRepository.findByIdAndMemberId(memoId, memberId)
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
