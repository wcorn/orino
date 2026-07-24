package ds.project.orino.planner.lifelog.flow.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.lifelog.entity.Flow;
import ds.project.orino.domain.planner.lifelog.entity.FlowMoment;
import ds.project.orino.domain.planner.lifelog.entity.FlowStatus;
import ds.project.orino.domain.planner.lifelog.entity.Moment;
import ds.project.orino.domain.planner.lifelog.entity.MomentPhoto;
import ds.project.orino.domain.planner.lifelog.repository.FlowMomentRepository;
import ds.project.orino.domain.planner.lifelog.repository.FlowRepository;
import ds.project.orino.domain.planner.lifelog.repository.MomentPhotoRepository;
import ds.project.orino.domain.planner.lifelog.repository.MomentRepository;
import ds.project.orino.planner.lifelog.flow.dto.FlowCreateRequest;
import ds.project.orino.planner.lifelog.flow.dto.FlowDetail;
import ds.project.orino.planner.lifelog.flow.dto.FlowSummary;
import ds.project.orino.planner.lifelog.flow.dto.FlowUpdateRequest;
import ds.project.orino.planner.lifelog.image.service.LifelogImageStorageService;
import ds.project.orino.planner.lifelog.moment.dto.MomentCard;
import ds.project.orino.planner.lifelog.moment.dto.MomentPhotoResponse;
import ds.project.orino.planner.lifelog.moment.service.MomentCardAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 흐름(Flow) CRUD와 N:M 담기/빼기/순서 조정.
 *
 * <p>순서 규칙: {@code sort_order} 오름차순, 동률이면 {@code occurred_at} — 즉 <b>기본은 시간순 서사</b>,
 * 사용자가 순서를 바꾸면(reorder) sort_order가 우선한다. 기간(started/ended)은 담긴 기록에서 유도해
 * 저장하고, 커버는 없으면 담긴 첫(시간순) 사진으로 대체한다.
 */
@Service
@Transactional(readOnly = true)
public class FlowService {

    private final FlowRepository flowRepository;
    private final FlowMomentRepository flowMomentRepository;
    private final MomentRepository momentRepository;
    private final MomentPhotoRepository photoRepository;
    private final MomentCardAssembler assembler;
    private final LifelogImageStorageService imageStorageService;

    public FlowService(FlowRepository flowRepository,
                       FlowMomentRepository flowMomentRepository,
                       MomentRepository momentRepository,
                       MomentPhotoRepository photoRepository,
                       MomentCardAssembler assembler,
                       LifelogImageStorageService imageStorageService) {
        this.flowRepository = flowRepository;
        this.flowMomentRepository = flowMomentRepository;
        this.momentRepository = momentRepository;
        this.photoRepository = photoRepository;
        this.assembler = assembler;
        this.imageStorageService = imageStorageService;
    }

    @Transactional
    public FlowSummary create(Long memberId, FlowCreateRequest request) {
        Flow flow = flowRepository.save(new Flow(memberId, request.title(), request.description()));
        return new FlowSummary(flow.getId(), flow.getTitle(), flow.getDescription(),
                null, null, null, 0, flow.getStatus());
    }

    public List<FlowSummary> list(Long memberId, FlowStatus status) {
        List<Flow> flows = status == null
                ? flowRepository.findAllByMemberIdOrderByStartedAtDescIdDesc(memberId)
                : flowRepository.findAllByMemberIdAndStatusOrderByStartedAtDescIdDesc(memberId, status);
        return buildSummaries(flows);
    }

    public FlowDetail detail(Long memberId, Long flowId) {
        Flow flow = getOwned(memberId, flowId);
        List<Moment> ordered = orderedMoments(flowId);
        List<MomentCard> cards = assembler.toCards(ordered);
        return new FlowDetail(flow.getId(), flow.getTitle(), flow.getDescription(),
                resolveCoverUrl(flow, cards), flow.getStartedAt(), flow.getEndedAt(),
                flow.getStatus(), cards);
    }

    @Transactional
    public FlowSummary update(Long memberId, Long flowId, FlowUpdateRequest request) {
        Flow flow = getOwned(memberId, flowId);
        // 기간은 담긴 기록에서 유도된 값 유지(사용자가 직접 바꾸지 않는다).
        flow.update(request.title(), request.description(), request.coverObjectKey(),
                flow.getStartedAt(), flow.getEndedAt(), request.status());
        return summaryOf(flow, flowMomentRepository.findAllByFlowIdOrderBySortOrderAscIdAsc(flowId).size());
    }

    /** 흐름 삭제 — flow_moment만 CASCADE로 제거되고 담겼던 기록은 보존된다. */
    @Transactional
    public void delete(Long memberId, Long flowId) {
        flowRepository.delete(getOwned(memberId, flowId));
    }

    @Transactional
    public FlowDetail addMoments(Long memberId, Long flowId, Collection<Long> requestedMomentIds) {
        getOwned(memberId, flowId);
        for (Long momentId : dedupe(requestedMomentIds)) {
            // 소유한 기록만, 이미 담긴 건 건너뛴다(멱등).
            boolean owned = momentRepository.findByIdAndMemberId(momentId, memberId).isPresent();
            if (owned && !flowMomentRepository.existsByFlowIdAndMomentId(flowId, momentId)) {
                flowMomentRepository.save(new FlowMoment(flowId, momentId, 0));
            }
        }
        recomputePeriod(flowId);
        return detail(memberId, flowId);
    }

    @Transactional
    public void removeMoment(Long memberId, Long flowId, Long momentId) {
        getOwned(memberId, flowId);
        flowMomentRepository.deleteByFlowIdAndMomentId(flowId, momentId);
        recomputePeriod(flowId);
    }

    @Transactional
    public FlowDetail reorder(Long memberId, Long flowId, List<Long> momentIds) {
        getOwned(memberId, flowId);
        Map<Long, FlowMoment> byMoment = flowMomentRepository.findAllByFlowIdOrderBySortOrderAscIdAsc(flowId)
                .stream().collect(Collectors.toMap(FlowMoment::getMomentId, fm -> fm));
        int order = 0;
        for (Long momentId : momentIds) {
            FlowMoment fm = byMoment.get(momentId);
            if (fm != null) {
                fm.updateSortOrder(order++);
            }
        }
        return detail(memberId, flowId);
    }

    // ---------------- helpers ----------------

    private Flow getOwned(Long memberId, Long flowId) {
        return flowRepository.findByIdAndMemberId(flowId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LIFELOG_FLOW_NOT_FOUND));
    }

    /** 흐름의 기록을 (sort_order, occurred_at, id) 순으로 정렬해 반환. */
    private List<Moment> orderedMoments(Long flowId) {
        List<FlowMoment> memberships = flowMomentRepository.findAllByFlowIdOrderBySortOrderAscIdAsc(flowId);
        if (memberships.isEmpty()) {
            return List.of();
        }
        Map<Long, Moment> moments = momentRepository.findAllById(
                        memberships.stream().map(FlowMoment::getMomentId).toList()).stream()
                .collect(Collectors.toMap(Moment::getId, m -> m));

        return memberships.stream()
                .filter(fm -> moments.containsKey(fm.getMomentId()))
                .sorted(Comparator
                        .comparingInt(FlowMoment::getSortOrder)
                        .thenComparing(fm -> moments.get(fm.getMomentId()).getOccurredAt())
                        .thenComparing(FlowMoment::getMomentId))
                .map(fm -> moments.get(fm.getMomentId()))
                .toList();
    }

    /** 담긴 기록의 min/max occurred_at을 흐름 기간으로 저장한다(비면 null). */
    private void recomputePeriod(Long flowId) {
        Flow flow = flowRepository.findById(flowId).orElseThrow();
        List<Long> momentIds = flowMomentRepository.findAllByFlowIdOrderBySortOrderAscIdAsc(flowId)
                .stream().map(FlowMoment::getMomentId).toList();
        if (momentIds.isEmpty()) {
            flow.updatePeriod(null, null);
            return;
        }
        List<Instant> times = momentRepository.findAllById(momentIds).stream()
                .map(Moment::getOccurredAt).sorted().toList();
        flow.updatePeriod(times.get(0), times.get(times.size() - 1));
    }

    private String resolveCoverUrl(Flow flow, List<MomentCard> orderedCards) {
        if (flow.getCoverObjectKey() != null) {
            return imageStorageService.toPublicUrl(flow.getCoverObjectKey());
        }
        // 커버 미지정 → 시간순 첫 기록의 첫 사진.
        for (MomentCard card : orderedCards) {
            if (!card.photos().isEmpty()) {
                MomentPhotoResponse p = card.photos().get(0);
                return p.thumbUrl() != null ? p.thumbUrl() : p.url();
            }
        }
        return null;
    }

    private FlowSummary summaryOf(Flow flow, long momentCount) {
        return new FlowSummary(flow.getId(), flow.getTitle(), flow.getDescription(),
                flow.getCoverObjectKey() != null ? imageStorageService.toPublicUrl(flow.getCoverObjectKey()) : null,
                flow.getStartedAt(), flow.getEndedAt(), momentCount, flow.getStatus());
    }

    /** 목록 요약을 배치로 만든다(커버 fallback·카운트를 몇 번의 쿼리로). */
    private List<FlowSummary> buildSummaries(List<Flow> flows) {
        if (flows.isEmpty()) {
            return List.of();
        }
        List<Long> flowIds = flows.stream().map(Flow::getId).toList();
        Map<Long, List<FlowMoment>> membershipsByFlow =
                flowMomentRepository.findAllByFlowIdIn(flowIds).stream()
                        .collect(Collectors.groupingBy(FlowMoment::getFlowId));

        // 커버 fallback 대상(커버 미지정 흐름)의 시간순 첫 기록.
        List<Long> allMomentIds = membershipsByFlow.values().stream()
                .flatMap(List::stream).map(FlowMoment::getMomentId).distinct().toList();
        Map<Long, Moment> moments = allMomentIds.isEmpty() ? Map.of()
                : momentRepository.findAllById(allMomentIds).stream()
                        .collect(Collectors.toMap(Moment::getId, m -> m));

        Map<Long, Long> firstMomentByFlow = new HashMap<>();
        for (Flow flow : flows) {
            if (flow.getCoverObjectKey() != null) {
                continue;
            }
            membershipsByFlow.getOrDefault(flow.getId(), List.of()).stream()
                    .map(fm -> moments.get(fm.getMomentId()))
                    .filter(Objects::nonNull)
                    .min(Comparator.comparing(Moment::getOccurredAt).thenComparing(Moment::getId))
                    .ifPresent(m -> firstMomentByFlow.put(flow.getId(), m.getId()));
        }
        Map<Long, MomentPhoto> firstPhotoByMoment = firstMomentByFlow.isEmpty() ? Map.of()
                : photoRepository.findAllByMomentIdInOrderBySortOrderAscIdAsc(
                                new ArrayList<>(new LinkedHashSet<>(firstMomentByFlow.values()))).stream()
                        .collect(Collectors.toMap(MomentPhoto::getMomentId, p -> p, (a, b) -> a));

        List<FlowSummary> summaries = new ArrayList<>();
        for (Flow flow : flows) {
            long count = membershipsByFlow.getOrDefault(flow.getId(), List.of()).size();
            summaries.add(new FlowSummary(flow.getId(), flow.getTitle(), flow.getDescription(),
                    coverUrlForList(flow, firstMomentByFlow, firstPhotoByMoment),
                    flow.getStartedAt(), flow.getEndedAt(), count, flow.getStatus()));
        }
        return summaries;
    }

    private String coverUrlForList(Flow flow, Map<Long, Long> firstMomentByFlow,
                                   Map<Long, MomentPhoto> firstPhotoByMoment) {
        if (flow.getCoverObjectKey() != null) {
            return imageStorageService.toPublicUrl(flow.getCoverObjectKey());
        }
        Long firstMomentId = firstMomentByFlow.get(flow.getId());
        if (firstMomentId == null) {
            return null;
        }
        MomentPhoto photo = firstPhotoByMoment.get(firstMomentId);
        if (photo == null) {
            return null;
        }
        String key = photo.getThumbKey() != null ? photo.getThumbKey() : photo.getObjectKey();
        return imageStorageService.toPublicUrl(key);
    }

    private Set<Long> dedupe(Collection<Long> ids) {
        Set<Long> result = new LinkedHashSet<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null) {
                    result.add(id);
                }
            }
        }
        return result;
    }
}
