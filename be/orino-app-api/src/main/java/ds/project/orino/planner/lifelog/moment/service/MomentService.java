package ds.project.orino.planner.lifelog.moment.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.lifelog.entity.Moment;
import ds.project.orino.domain.planner.lifelog.entity.MomentPhoto;
import ds.project.orino.domain.planner.lifelog.entity.MomentTag;
import ds.project.orino.domain.planner.lifelog.repository.MomentPhotoRepository;
import ds.project.orino.domain.planner.lifelog.repository.MomentRepository;
import ds.project.orino.domain.planner.lifelog.repository.MomentTagRepository;
import ds.project.orino.planner.lifelog.image.service.LifelogImageStorageService;
import ds.project.orino.planner.lifelog.moment.dto.FeedResponse;
import ds.project.orino.planner.lifelog.moment.dto.MomentCard;
import ds.project.orino.planner.lifelog.moment.dto.MomentPhotoRequest;
import ds.project.orino.planner.lifelog.moment.dto.MomentWriteRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 기록(Moment) CRUD·피드. 사진·태그는 별도 테이블에 저장하고 카드 조립은 {@link MomentCardAssembler}가
 * 배치로 한다. 사진 삭제는 DB CASCADE로 행이 지워진 뒤 MinIO 오브젝트를 best-effort로 정리한다.
 */
@Service
@Transactional(readOnly = true)
public class MomentService {

    private static final int DEFAULT_FEED_SIZE = 20;
    private static final int MAX_FEED_SIZE = 50;

    private final MomentRepository momentRepository;
    private final MomentPhotoRepository photoRepository;
    private final MomentTagRepository tagRepository;
    private final MomentCardAssembler assembler;
    private final LifelogImageStorageService imageStorageService;
    private final Clock clock;

    public MomentService(MomentRepository momentRepository,
                         MomentPhotoRepository photoRepository,
                         MomentTagRepository tagRepository,
                         MomentCardAssembler assembler,
                         LifelogImageStorageService imageStorageService,
                         Clock clock) {
        this.momentRepository = momentRepository;
        this.photoRepository = photoRepository;
        this.tagRepository = tagRepository;
        this.assembler = assembler;
        this.imageStorageService = imageStorageService;
        this.clock = clock;
    }

    @Transactional
    public MomentCard create(Long memberId, MomentWriteRequest request) {
        validate(request);
        Instant occurredAt = request.occurredAt() != null ? request.occurredAt() : clock.instant();

        Moment moment = momentRepository.save(new Moment(
                memberId, occurredAt, blankToNull(request.body()), request.mood(),
                request.lat(), request.lng(), blankToNull(request.placeName())));

        savePhotos(moment.getId(), request.photos());
        saveTags(moment.getId(), request.tags());
        return assembler.toCard(moment);
    }

    public MomentCard findOne(Long memberId, Long momentId) {
        return assembler.toCard(getOwned(memberId, momentId));
    }

    public FeedResponse feed(Long memberId, String cursor, Integer size,
                             String tag, Instant from, Instant to) {
        int limit = clampSize(size);
        FeedCursor decoded = FeedCursor.decode(cursor);

        List<Moment> rows = momentRepository.findFeed(
                memberId, from, to, blankToNull(tag),
                decoded == null ? null : decoded.occurredAt(),
                decoded == null ? null : decoded.id(),
                PageRequest.of(0, limit + 1));

        String nextCursor = null;
        if (rows.size() > limit) {
            Moment last = rows.get(limit - 1);
            nextCursor = new FeedCursor(last.getOccurredAt(), last.getId()).encode();
            rows = rows.subList(0, limit);
        }
        return new FeedResponse(assembler.toCards(rows), nextCursor);
    }

    @Transactional
    public MomentCard update(Long memberId, Long momentId, MomentWriteRequest request) {
        validate(request);
        Moment moment = getOwned(memberId, momentId);
        Instant occurredAt = request.occurredAt() != null ? request.occurredAt() : moment.getOccurredAt();
        moment.update(occurredAt, blankToNull(request.body()), request.mood(),
                request.lat(), request.lng(), blankToNull(request.placeName()));

        // 사진·태그 전체 치환. 제거된 오브젝트 key는 MinIO에서 best-effort로 지운다.
        Set<String> oldKeys = collectKeys(photoRepository.findAllByMomentIdOrderBySortOrderAscIdAsc(momentId));
        photoRepository.deleteByMomentId(momentId);
        tagRepository.deleteByMomentId(momentId);
        savePhotos(momentId, request.photos());
        saveTags(momentId, request.tags());

        Set<String> newKeys = collectKeys(photoRepository.findAllByMomentIdOrderBySortOrderAscIdAsc(momentId));
        oldKeys.removeAll(newKeys);
        imageStorageService.deleteObjects(oldKeys);

        return assembler.toCard(moment);
    }

    @Transactional
    public void delete(Long memberId, Long momentId) {
        Moment moment = getOwned(memberId, momentId);
        Set<String> keys = collectKeys(photoRepository.findAllByMomentIdOrderBySortOrderAscIdAsc(momentId));
        // DB FK CASCADE로 사진·태그·흐름소속 행이 함께 지워진다.
        momentRepository.delete(moment);
        imageStorageService.deleteObjects(keys);
    }

    /** 태그 자동완성 — 멤버가 쓴 태그 중 접두어 일치(중복 제거·정렬). */
    public List<String> autocompleteTags(Long memberId, String query) {
        String prefix = query == null ? "" : query.trim();
        return tagRepository.findDistinctNamesByMemberIdAndPrefix(memberId, prefix + "%");
    }

    private Moment getOwned(Long memberId, Long momentId) {
        return momentRepository.findByIdAndMemberId(momentId, memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.LIFELOG_MOMENT_NOT_FOUND));
    }

    private void validate(MomentWriteRequest request) {
        boolean hasLat = request.lat() != null;
        boolean hasLng = request.lng() != null;
        if (hasLat != hasLng) {
            throw new CustomException(ErrorCode.LIFELOG_INVALID_COORDINATE);
        }
        boolean noBody = blankToNull(request.body()) == null;
        boolean noPhotos = request.photos() == null || request.photos().isEmpty();
        if (noBody && noPhotos) {
            throw new CustomException(ErrorCode.LIFELOG_EMPTY_MOMENT);
        }
    }

    private void savePhotos(Long momentId, List<MomentPhotoRequest> photos) {
        if (photos == null || photos.isEmpty()) {
            return;
        }
        List<MomentPhoto> entities = new ArrayList<>();
        for (int i = 0; i < photos.size(); i++) {
            MomentPhotoRequest p = photos.get(i);
            int sortOrder = p.sortOrder() != null ? p.sortOrder() : i;
            entities.add(new MomentPhoto(momentId, p.objectKey(), p.thumbKey(),
                    p.width(), p.height(), p.exifTakenAt(), p.exifLat(), p.exifLng(), sortOrder));
        }
        photoRepository.saveAll(entities);
    }

    private void saveTags(Long momentId, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                seen.add(trimmed);
            }
        }
        List<MomentTag> entities = seen.stream().map(name -> new MomentTag(momentId, name)).toList();
        tagRepository.saveAll(entities);
    }

    private Set<String> collectKeys(List<MomentPhoto> photos) {
        Set<String> keys = new HashSet<>();
        for (MomentPhoto p : photos) {
            keys.add(p.getObjectKey());
            if (p.getThumbKey() != null) {
                keys.add(p.getThumbKey());
            }
        }
        return keys;
    }

    private int clampSize(Integer size) {
        if (size == null) {
            return DEFAULT_FEED_SIZE;
        }
        return Math.max(1, Math.min(MAX_FEED_SIZE, size));
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
