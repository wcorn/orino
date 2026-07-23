package ds.project.orino.planner.lifelog.moment.service;

import ds.project.orino.domain.planner.lifelog.entity.Flow;
import ds.project.orino.domain.planner.lifelog.entity.FlowMoment;
import ds.project.orino.domain.planner.lifelog.entity.Moment;
import ds.project.orino.domain.planner.lifelog.entity.MomentPhoto;
import ds.project.orino.domain.planner.lifelog.entity.MomentTag;
import ds.project.orino.domain.planner.lifelog.repository.FlowMomentRepository;
import ds.project.orino.domain.planner.lifelog.repository.FlowRepository;
import ds.project.orino.domain.planner.lifelog.repository.MomentPhotoRepository;
import ds.project.orino.domain.planner.lifelog.repository.MomentTagRepository;
import ds.project.orino.planner.lifelog.image.service.LifelogImageStorageService;
import ds.project.orino.planner.lifelog.moment.dto.FlowRef;
import ds.project.orino.planner.lifelog.moment.dto.MomentCard;
import ds.project.orino.planner.lifelog.moment.dto.MomentPhotoResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Moment 엔티티 목록을 {@link MomentCard}로 조립한다. 사진·태그·소속 흐름을 <b>배치로</b> 한 번에
 * 읽어 N+1을 피한다(피드가 주 사용처).
 */
@Component
public class MomentCardAssembler {

    private final MomentPhotoRepository photoRepository;
    private final MomentTagRepository tagRepository;
    private final FlowMomentRepository flowMomentRepository;
    private final FlowRepository flowRepository;
    private final LifelogImageStorageService imageStorageService;

    public MomentCardAssembler(MomentPhotoRepository photoRepository,
                               MomentTagRepository tagRepository,
                               FlowMomentRepository flowMomentRepository,
                               FlowRepository flowRepository,
                               LifelogImageStorageService imageStorageService) {
        this.photoRepository = photoRepository;
        this.tagRepository = tagRepository;
        this.flowMomentRepository = flowMomentRepository;
        this.flowRepository = flowRepository;
        this.imageStorageService = imageStorageService;
    }

    public MomentCard toCard(Moment moment) {
        return toCards(List.of(moment)).get(0);
    }

    /** 입력 순서를 유지하며 카드로 변환한다. */
    public List<MomentCard> toCards(List<Moment> moments) {
        if (moments.isEmpty()) {
            return List.of();
        }
        List<Long> momentIds = moments.stream().map(Moment::getId).toList();

        Map<Long, List<MomentPhoto>> photosByMoment =
                photoRepository.findAllByMomentIdInOrderBySortOrderAscIdAsc(momentIds).stream()
                        .collect(Collectors.groupingBy(MomentPhoto::getMomentId));

        Map<Long, List<MomentTag>> tagsByMoment =
                tagRepository.findAllByMomentIdIn(momentIds).stream()
                        .collect(Collectors.groupingBy(MomentTag::getMomentId));

        List<FlowMoment> memberships = flowMomentRepository.findAllByMomentIdIn(momentIds);
        Map<Long, String> flowTitles = flowRepository.findAllById(
                        memberships.stream().map(FlowMoment::getFlowId).distinct().toList()).stream()
                .collect(Collectors.toMap(Flow::getId, Flow::getTitle));
        Map<Long, List<FlowMoment>> flowsByMoment =
                memberships.stream().collect(Collectors.groupingBy(FlowMoment::getMomentId));

        return moments.stream()
                .map(m -> new MomentCard(
                        m.getId(),
                        m.getOccurredAt(),
                        m.getBody(),
                        m.getMood(),
                        m.getLat(),
                        m.getLng(),
                        m.getPlaceName(),
                        tagNames(tagsByMoment.get(m.getId())),
                        photoResponses(photosByMoment.get(m.getId())),
                        flowRefs(flowsByMoment.get(m.getId()), flowTitles),
                        m.getCreatedAt()))
                .toList();
    }

    private List<String> tagNames(List<MomentTag> tags) {
        return tags == null ? List.of() : tags.stream().map(MomentTag::getName).toList();
    }

    private List<MomentPhotoResponse> photoResponses(List<MomentPhoto> photos) {
        if (photos == null) {
            return List.of();
        }
        return photos.stream()
                .map(p -> new MomentPhotoResponse(
                        p.getId(),
                        imageStorageService.toPublicUrl(p.getObjectKey()),
                        imageStorageService.toPublicUrl(p.getThumbKey()),
                        p.getWidth(),
                        p.getHeight(),
                        p.getSortOrder()))
                .toList();
    }

    private List<FlowRef> flowRefs(List<FlowMoment> memberships, Map<Long, String> flowTitles) {
        if (memberships == null) {
            return List.of();
        }
        return memberships.stream()
                .map(FlowMoment::getFlowId)
                .map(id -> new FlowRef(id, flowTitles.get(id)))
                .filter(ref -> ref.title() != null)
                .toList();
    }
}
