package ds.project.orino.planner.review.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.flashcard.entity.Flashcard;
import ds.project.orino.domain.planner.flashcard.repository.FlashcardRepository;
import ds.project.orino.domain.planner.google.entity.GoogleAccount;
import ds.project.orino.domain.planner.google.repository.GoogleAccountRepository;
import ds.project.orino.domain.planner.material.entity.StudyMaterial;
import ds.project.orino.domain.planner.material.repository.StudyMaterialRepository;
import ds.project.orino.domain.planner.review.entity.ReviewCalendarMirror;
import ds.project.orino.domain.planner.review.entity.ReviewSchedule;
import ds.project.orino.domain.planner.review.entity.ReviewStatus;
import ds.project.orino.domain.planner.review.repository.ReviewCalendarMirrorRepository;
import ds.project.orino.domain.planner.review.repository.ReviewScheduleRepository;
import ds.project.orino.planner.google.client.GoogleCalendarClient;
import ds.project.orino.planner.review.dto.ReviewMirrorStatusResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 복습 → 보조 캘린더("orino 복습") 단방향 미러. orino가 진실, Google은 투영.
 *
 * <p>이벤트 기반 멱등 reconcile: dueDate(04:00 롤오버)당 PENDING 복습을 "복습 N개" 종일 묶음 이벤트 1개로
 * upsert하고, 0이면 삭제한다. AGAIN(now+10분)은 04:00 정각이 아니라 자연히 제외된다. 항상 DB 진실 기준
 * 재계산하므로 Google에서 사용자가 이벤트를 지워도(404) 재생성으로 self-heal한다.
 *
 * <p>미러는 핵심 복습 동작의 부수효과이므로, 호출부 트랜잭션 <b>커밋 후</b>({@link #reconcileAfterCommit})
 * 실행한다 — Google 장애가 복습 생성/완료를 롤백시키지 않게 한다(드리프트는 다음 reconcile가 치유).
 */
@Service
public class ReviewMirrorService {

    private static final String CALENDAR_SUMMARY_PREFIX = "복습 ";
    private static final String CALENDAR_SUMMARY_SUFFIX = "개";
    private static final String NO_MATERIAL_LABEL = "(자료 없음)";
    /** 보조 캘린더 제목. */
    private static final String SECONDARY_CALENDAR_SUMMARY = "orino 복습";

    private final GoogleAccountRepository googleAccountRepository;
    private final ReviewScheduleRepository reviewScheduleRepository;
    private final ReviewCalendarMirrorRepository mirrorRepository;
    private final FlashcardRepository flashcardRepository;
    private final StudyMaterialRepository studyMaterialRepository;
    private final GoogleCalendarClient calendarClient;
    private final Clock clock;
    /** 커밋 후/프록시 경유 호출로 {@link #reconcileDate}의 {@code REQUIRES_NEW} 트랜잭션을 적용하기 위한 self 참조. */
    private final ReviewMirrorService self;

    public ReviewMirrorService(GoogleAccountRepository googleAccountRepository,
                               ReviewScheduleRepository reviewScheduleRepository,
                               ReviewCalendarMirrorRepository mirrorRepository,
                               FlashcardRepository flashcardRepository,
                               StudyMaterialRepository studyMaterialRepository,
                               GoogleCalendarClient calendarClient,
                               Clock clock,
                               @Lazy ReviewMirrorService self) {
        this.googleAccountRepository = googleAccountRepository;
        this.reviewScheduleRepository = reviewScheduleRepository;
        this.mirrorRepository = mirrorRepository;
        this.flashcardRepository = flashcardRepository;
        this.studyMaterialRepository = studyMaterialRepository;
        this.calendarClient = calendarClient;
        this.clock = clock;
        this.self = self;
    }

    /**
     * 미러를 켠다. 보조 캘린더가 없으면 생성·저장하고(enabled 커밋), 모든 PENDING 복습 날짜를 백필 reconcile한다.
     * 미연동(또는 revoked)이면 {@link ErrorCode#GOOGLE_NOT_CONNECTED}(409).
     *
     * <p>활성화 커밋을 백필보다 먼저 끝내야 reconcileDate(REQUIRES_NEW)가 enabled=true를 본다.
     */
    public ReviewMirrorStatusResponse enableMirror(Long memberId, ZoneId zone) {
        String calendarId = self.activateMirror(memberId);
        backfill(memberId, zone);
        return new ReviewMirrorStatusResponse(true, calendarId);
    }

    /** 미러를 끈다. mirror 이벤트·행을 모두 정리하고 enabled=0으로 둔다(빈 보조 캘린더는 보존). 미연동이면 409. */
    @Transactional
    public ReviewMirrorStatusResponse disableMirror(Long memberId) {
        GoogleAccount account = requireConnected(memberId);
        String calendarId = account.getReviewCalendarId();
        if (calendarId != null) {
            for (ReviewCalendarMirror mirror : mirrorRepository.findAllByMemberId(memberId)) {
                deleteEventQuietly(memberId, calendarId, mirror.getGoogleEventId());
            }
            mirrorRepository.deleteByMemberId(memberId);
        }
        account.disableReviewMirror();
        googleAccountRepository.save(account);
        return new ReviewMirrorStatusResponse(false, calendarId);
    }

    /** 보조 캘린더를 보장(없으면 생성)하고 enabled=true로 만든 뒤 calendarId를 반환한다(독립 트랜잭션으로 커밋). */
    @Transactional
    public String activateMirror(Long memberId) {
        GoogleAccount account = requireConnected(memberId);
        String calendarId = account.getReviewCalendarId();
        if (calendarId == null) {
            calendarId = calendarClient.createSecondaryCalendar(memberId, SECONDARY_CALENDAR_SUMMARY);
        }
        account.enableReviewMirror(calendarId);
        googleAccountRepository.save(account);
        return calendarId;
    }

    /** 모든 PENDING 복습의 dueDate(04:00 롤오버 날짜)를 멱등 reconcile한다(이미 커밋된 enabled 계정 기준). */
    private void backfill(Long memberId, ZoneId zone) {
        Set<LocalDate> dueDates = reviewScheduleRepository
                .findAllByMemberIdAndStatus(memberId, ReviewStatus.PENDING).stream()
                .map(review -> review.getScheduledAt().atZone(zone).toLocalDate())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        dueDates.forEach(date -> self.reconcileDate(memberId, date, zone));
    }

    private GoogleAccount requireConnected(Long memberId) {
        return googleAccountRepository.findByMemberId(memberId)
                .filter(account -> !account.isRevoked())
                .orElseThrow(() -> new CustomException(ErrorCode.GOOGLE_NOT_CONNECTED));
    }

    /**
     * 주어진 dueDate들을 현재 트랜잭션 커밋 후 reconcile하도록 등록한다. 활성 트랜잭션이 없으면 즉시 실행한다.
     * 중복 날짜는 한 번만 처리한다.
     */
    public void reconcileAfterCommit(Long memberId, Collection<LocalDate> dueDates, ZoneId zone) {
        Set<LocalDate> dates = new LinkedHashSet<>(dueDates);
        if (dates.isEmpty()) {
            return;
        }
        // self 프록시 경유 호출이라야 reconcileDate의 REQUIRES_NEW 트랜잭션이 적용된다(직접 호출 시 self-invocation으로 무시됨).
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dates.forEach(date -> self.reconcileDate(memberId, date, zone));
                }
            });
        } else {
            dates.forEach(date -> self.reconcileDate(memberId, date, zone));
        }
    }

    /**
     * dueDate 묶음을 보조 캘린더와 동기화한다(멱등). 미러 비활성이거나 미연동이면 no-op.
     * N&gt;0이면 "복습 N개" 종일 이벤트를 upsert(없으면 insert, 있으면 patch, Google 404면 재생성),
     * N=0이면 이벤트·매핑을 삭제한다.
     *
     * <p>{@code REQUIRES_NEW} — 커밋 후(afterCommit) 호출 시 완료 중인 트랜잭션에 합류해 쓰기가 유실되지 않도록
     * 항상 새 트랜잭션에서 실행한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileDate(Long memberId, LocalDate dueDate, ZoneId zone) {
        GoogleAccount account = googleAccountRepository.findByMemberId(memberId).orElse(null);
        if (account == null || account.isRevoked()
                || !account.isReviewMirrorEnabled() || account.getReviewCalendarId() == null) {
            return;
        }
        String calendarId = account.getReviewCalendarId();

        Instant rollover = dueDate.atTime(ReviewSchedule.ROLLOVER_HOUR, 0).atZone(zone).toInstant();
        List<ReviewSchedule> pending = reviewScheduleRepository
                .findAllByMemberIdAndStatusAndScheduledAt(memberId, ReviewStatus.PENDING, rollover);
        Optional<ReviewCalendarMirror> existing = mirrorRepository.findByMemberIdAndDueDate(memberId, dueDate);

        if (pending.isEmpty()) {
            existing.ifPresent(mirror -> {
                deleteEventQuietly(memberId, calendarId, mirror.getGoogleEventId());
                mirrorRepository.delete(mirror);
            });
            return;
        }

        int count = pending.size();
        String summary = CALENDAR_SUMMARY_PREFIX + count + CALENDAR_SUMMARY_SUFFIX;
        String description = buildDescription(pending);
        Instant now = clock.instant();

        if (existing.isPresent()) {
            ReviewCalendarMirror mirror = existing.get();
            String eventId = patchOrRecreate(
                    memberId, calendarId, mirror.getGoogleEventId(), summary, description, dueDate);
            mirror.sync(eventId, count, now);
            mirrorRepository.save(mirror);
        } else {
            String eventId = calendarClient.insertAllDayEvent(
                    memberId, calendarId, summary, description, dueDate);
            mirrorRepository.save(new ReviewCalendarMirror(memberId, dueDate, eventId, count, now));
        }
    }

    /** patch를 시도하고, Google에서 이벤트가 지워졌으면(404) 재생성해 새 eventId를 반환한다(self-heal). */
    private String patchOrRecreate(Long memberId, String calendarId, String eventId,
                                   String summary, String description, LocalDate dueDate) {
        try {
            calendarClient.patchAllDayEvent(memberId, calendarId, eventId, summary, description);
            return eventId;
        } catch (CustomException e) {
            if (e.getErrorCode() == ErrorCode.RESOURCE_NOT_FOUND) {
                return calendarClient.insertAllDayEvent(memberId, calendarId, summary, description, dueDate);
            }
            throw e;
        }
    }

    /** 이벤트 삭제 — 이미 Google에 없으면(404) 무시한다. */
    private void deleteEventQuietly(Long memberId, String calendarId, String eventId) {
        try {
            calendarClient.deleteEvent(memberId, calendarId, eventId);
        } catch (CustomException e) {
            if (e.getErrorCode() != ErrorCode.RESOURCE_NOT_FOUND) {
                throw e;
            }
        }
    }

    /** 자료 제목별 복습 개수 설명("제목: N개" 줄 목록). 링크 필드는 아직 데이터 모델에 없어 제외. */
    private String buildDescription(List<ReviewSchedule> pending) {
        List<Long> cardIds = pending.stream()
                .map(ReviewSchedule::getFlashcardId).distinct().toList();
        Map<Long, Flashcard> cardById = flashcardRepository.findAllByIdIn(cardIds).stream()
                .collect(Collectors.toMap(Flashcard::getId, Function.identity()));
        List<Long> materialIds = cardById.values().stream()
                .map(Flashcard::getMaterialId).distinct().toList();
        Map<Long, StudyMaterial> materialById = studyMaterialRepository.findAllByIdIn(materialIds).stream()
                .collect(Collectors.toMap(StudyMaterial::getId, Function.identity()));

        Map<String, Integer> countByTitle = new TreeMap<>();
        for (ReviewSchedule review : pending) {
            countByTitle.merge(materialTitle(review, cardById, materialById), 1, Integer::sum);
        }
        return countByTitle.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue() + CALENDAR_SUMMARY_SUFFIX)
                .collect(Collectors.joining("\n"));
    }

    private String materialTitle(ReviewSchedule review,
                                 Map<Long, Flashcard> cardById, Map<Long, StudyMaterial> materialById) {
        Flashcard card = cardById.get(review.getFlashcardId());
        if (card == null) {
            return NO_MATERIAL_LABEL;
        }
        StudyMaterial material = materialById.get(card.getMaterialId());
        return material == null ? NO_MATERIAL_LABEL : material.getTitle();
    }
}
