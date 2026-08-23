package ds.project.orino.planner.shortlink.service;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.shortlink.entity.Shortlink;
import ds.project.orino.domain.planner.shortlink.entity.ShortlinkStatus;
import ds.project.orino.domain.planner.shortlink.entity.ShortlinkTag;
import ds.project.orino.domain.planner.shortlink.entity.ShortlinkTargetHistory;
import ds.project.orino.domain.planner.shortlink.repository.ShortlinkRepository;
import ds.project.orino.domain.planner.shortlink.repository.ShortlinkTagRepository;
import ds.project.orino.domain.planner.shortlink.repository.ShortlinkTargetHistoryRepository;
import ds.project.orino.planner.shortlink.config.ShortlinkProperties;
import ds.project.orino.planner.shortlink.dto.CreatedLink;
import ds.project.orino.planner.shortlink.dto.FavoriteResponse;
import ds.project.orino.planner.shortlink.dto.LinkState;
import ds.project.orino.planner.shortlink.dto.LinkStatsResponse;
import ds.project.orino.planner.shortlink.dto.LinkSummary;
import ds.project.orino.planner.shortlink.dto.ListStatusFilter;
import ds.project.orino.planner.shortlink.dto.OgPreview;
import ds.project.orino.planner.shortlink.dto.ShortlinkCreateRequest;
import ds.project.orino.planner.shortlink.dto.ShortlinkDetail;
import ds.project.orino.planner.shortlink.dto.ShortlinkListResponse;
import ds.project.orino.planner.shortlink.dto.ShortlinkSummaryResponse;
import ds.project.orino.planner.shortlink.dto.ShortlinkUpdateRequest;
import ds.project.orino.planner.shortlink.dto.SlugAvailableResponse;
import ds.project.orino.planner.shortlink.dto.TagCount;
import ds.project.orino.planner.shortlink.dto.TargetHistoryEntry;
import ds.project.orino.planner.shortlink.dto.ToggleResponse;
import ds.project.orino.planner.shortlink.stats.VisitStatsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 링크 관리 API(API 설계 §2). 공개 리다이렉트(#1237)와는 표면이 완전히 다르다 —
 * 이쪽은 JWT 뒤에 있고 orino 공통 envelope를 쓴다.
 *
 * <p>이 서비스가 지키는 것 셋:
 * <ul>
 *   <li><b>슬러그는 불변</b>이다. 바꾸는 경로가 없다(명세 §5.2)</li>
 *   <li><b>삭제는 소프트</b>다. 행이 남아 {@code UNIQUE(slug)}가 재발급을 막는다(§3.1)</li>
 *   <li><b>목적지가 실제로 바뀔 때만</b> 이력을 남긴다. 같은 값 재전송은 이력이 늘지 않는다</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ShortlinkService {

    private static final int MAX_TAG_LENGTH = 50;
    /** 화면 기본 범위(화면 설계 §5의 「최근 30일 일별 막대」). */
    private static final int DEFAULT_RANGE_DAYS = 30;
    /** 1년. 그보다 긴 범위를 물어봐도 원시는 90일이라 유입 경로는 어차피 비어 있다. */
    private static final int MAX_RANGE_DAYS = 365;

    private final ShortlinkRepository shortlinkRepository;
    private final ShortlinkTagRepository tagRepository;
    private final ShortlinkTargetHistoryRepository historyRepository;
    private final SlugGenerator slugGenerator;
    private final TargetUrlValidator targetUrlValidator;
    private final ShortlinkProperties properties;
    private final VisitStatsService visitStatsService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final Clock clock;

    public ShortlinkService(ShortlinkRepository shortlinkRepository,
                            ShortlinkTagRepository tagRepository,
                            ShortlinkTargetHistoryRepository historyRepository,
                            SlugGenerator slugGenerator,
                            TargetUrlValidator targetUrlValidator,
                            ShortlinkProperties properties,
                            VisitStatsService visitStatsService,
                            BCryptPasswordEncoder passwordEncoder,
                            Clock clock) {
        this.shortlinkRepository = shortlinkRepository;
        this.tagRepository = tagRepository;
        this.historyRepository = historyRepository;
        this.slugGenerator = slugGenerator;
        this.targetUrlValidator = targetUrlValidator;
        this.properties = properties;
        this.visitStatsService = visitStatsService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /** {@code /select} 카드 메타. */
    public ShortlinkSummaryResponse summary(Long memberId) {
        return new ShortlinkSummaryResponse(
                shortlinkRepository.countByMemberIdAndDeletedAtIsNull(memberId),
                visitStatsService.visitsThisWeek(memberId),
                properties.baseUrl());
    }

    /**
     * 목록. 상태 칩 숫자({@code counts})는 <b>상태로 거르기 전</b>에 센다 — 걸러진 뒤에 세면
     * 지금 눌린 칩 말고는 숫자를 알 수 없다.
     */
    public ShortlinkListResponse list(Long memberId, String query, ListStatusFilter status, String tag) {
        Instant now = clock.instant();
        List<Shortlink> links = shortlinkRepository.search(memberId, likePattern(query), blankToNull(tag));
        Map<Long, List<String>> tagsByLink = loadTags(links);
        // 방문 수는 링크마다 세지 않고 한 번에 받아 온다 — 목록에서 N+1이 나는 자리다.
        List<Long> ids = links.stream().map(Shortlink::getId).toList();
        Map<Long, Long> visitTotals = visitStatsService.visitTotals(ids);
        Map<Long, Instant> lastVisits = visitStatsService.lastVisits(ids);

        long active = links.stream().filter(link -> stateOf(link, now) == LinkState.ACTIVE).count();
        ShortlinkListResponse.Counts counts =
                new ShortlinkListResponse.Counts(links.size(), active, links.size() - active);

        ListStatusFilter filter = status == null ? ListStatusFilter.ALL : status;
        List<Shortlink> visible = links.stream()
                .filter(link -> matches(filter, stateOf(link, now)))
                .toList();

        List<LinkSummary> favorites = visible.stream()
                .filter(Shortlink::isFavorite)
                .map(link -> toSummary(link, tagsByLink, visitTotals, lastVisits, now))
                .toList();
        // 즐겨찾기는 위 섹션에만 둔다 — 같은 카드가 두 번 나오면 목록 개수와 보이는 행이 어긋난다.
        List<LinkSummary> recent = visible.stream()
                .filter(link -> !link.isFavorite())
                .map(link -> toSummary(link, tagsByLink, visitTotals, lastVisits, now))
                .toList();

        return new ShortlinkListResponse(counts, favorites, recent);
    }

    /**
     * 발급. <b>최초 발급 이력 한 줄을 함께 넣는다</b>(명세 §5.1) — 그래서 이력이 빈 링크는 없다.
     */
    @Transactional
    public CreatedLink create(Long memberId, ShortlinkCreateRequest request) {
        String targetUrl = targetUrlValidator.validate(request.targetUrl());
        boolean custom = hasText(request.slug());
        String slug = custom ? requireAvailable(SlugPolicy.normalizeCustom(request.slug()))
                : slugGenerator.generate();

        Shortlink link = new Shortlink(memberId, slug, targetUrl, custom);
        link.updateMemo(blankToNull(request.memo()));
        link.updateExpiresAt(request.expiresAt());
        if (hasText(request.password())) {
            link.updatePasswordHash(passwordEncoder.encode(request.password()));
        }
        shortlinkRepository.save(link);

        List<String> tags = replaceTags(link.getId(), request.tags());
        Instant now = clock.instant();
        historyRepository.save(ShortlinkTargetHistory.initial(link.getId(), targetUrl, now));

        // 방금 만든 링크는 방문이 없다. 세러 가지 않는다.
        return CreatedLink.of(toSummary(link, tags, 0L, null, now));
    }

    public ShortlinkDetail detail(Long memberId, String slug) {
        Shortlink link = getOwned(memberId, slug);
        return toDetail(link);
    }

    /**
     * 방문 통계. 남의 링크·삭제된 링크는 여기서도 404다 — 통계가 존재를 알려주는 창구가
     * 되면 안 된다.
     *
     * @param range {@code 7d}·{@code 30d} 형식. 형식이 아니거나 범위를 벗어나면 30일로 본다
     */
    public LinkStatsResponse stats(Long memberId, String slug, String range) {
        Shortlink link = getOwned(memberId, slug);
        return visitStatsService.stats(link.getId(), parseRangeDays(range));
    }

    /**
     * 편집. {@code slug}는 요청 형태에 자리가 없어 실려 와도 무시된다(명세 §5.2).
     *
     * <p>{@code expiresAt}·{@code password}는 <b>보내지 않음 = 변경 없음</b>,
     * <b>{@code null} 명시 = 해제</b>다.
     */
    @Transactional
    public ShortlinkDetail update(Long memberId, String slug, ShortlinkUpdateRequest request) {
        Shortlink link = getOwned(memberId, slug);

        if (request.targetUrl() != null) {
            String targetUrl = targetUrlValidator.validate(request.targetUrl());
            // 같은 값 재전송은 이력이 늘지 않는다 — "메모만 고쳤는데 목적지를 갈아끼운 것처럼
            // 보이는" 이력은 나중에 이 화면을 읽는 사람을 속인다.
            if (link.changeTarget(targetUrl)) {
                historyRepository.save(new ShortlinkTargetHistory(link.getId(), targetUrl,
                        blankToNull(request.targetChangeReason()), clock.instant()));
            }
        }
        if (request.memo() != null) {
            link.updateMemo(blankToNull(request.memo()));
        }
        if (request.tags() != null) {
            replaceTags(link.getId(), request.tags());
        }
        applyExpiresAt(link, request.expiresAt());
        applyPassword(link, request.password());

        return toDetail(shortlinkRepository.saveAndFlush(link));
    }

    @Transactional
    public ToggleResponse toggle(Long memberId, String slug) {
        Shortlink link = getOwned(memberId, slug);
        link.toggleStatus();
        return new ToggleResponse(stateOf(shortlinkRepository.saveAndFlush(link), clock.instant()));
    }

    @Transactional
    public FavoriteResponse toggleFavorite(Long memberId, String slug) {
        Shortlink link = getOwned(memberId, slug);
        boolean favorite = link.toggleFavorite();
        shortlinkRepository.saveAndFlush(link);
        return new FavoriteResponse(favorite);
    }

    /**
     * 소프트 삭제. 태그·이력 행도 함께 남긴다 — 링크 행이 남으므로 지울 이유가 없고,
     * 지우면 나중에 "그 슬러그가 왜 막혀 있는지"를 설명할 근거가 사라진다.
     */
    @Transactional
    public void delete(Long memberId, String slug) {
        Shortlink link = getOwned(memberId, slug);
        link.softDelete(clock.instant());
        shortlinkRepository.saveAndFlush(link);
    }

    public List<TagCount> tags(Long memberId) {
        return tagRepository.countByMemberIdGroupByName(memberId).stream()
                .map(row -> new TagCount(row.getName(), row.getCount()))
                .toList();
    }

    /**
     * 커스텀 슬러그 중복 검사. <b>삭제된 링크의 슬러그도 사용 중</b>으로 답한다(명세 §3.1) —
     * 살아 있는지 삭제된 것인지는 구분해 알려주지 않는다.
     */
    public SlugAvailableResponse slugAvailable(String slug) {
        return new SlugAvailableResponse(
                !shortlinkRepository.existsBySlug(SlugPolicy.normalizeCustom(slug)));
    }

    /**
     * {@code 30d} → 30. <b>잘못된 값에 에러를 내지 않는다</b> — 통계는 참고치이고, 범위 하나
     * 때문에 화면이 비는 것보다 기본값으로 보여 주는 편이 낫다.
     */
    private int parseRangeDays(String range) {
        if (range == null || !range.endsWith("d")) {
            return DEFAULT_RANGE_DAYS;
        }
        try {
            int days = Integer.parseInt(range.substring(0, range.length() - 1));
            return days < 1 || days > MAX_RANGE_DAYS ? DEFAULT_RANGE_DAYS : days;
        } catch (NumberFormatException e) {
            return DEFAULT_RANGE_DAYS;
        }
    }

    private String requireAvailable(String slug) {
        if (shortlinkRepository.existsBySlug(slug)) {
            throw new CustomException(ErrorCode.SHORTLINK_SLUG_TAKEN);
        }
        return slug;
    }

    /**
     * 소유자의 살아 있는 링크만 돌려준다. <b>남의 링크도 404다</b> — 403으로 답하면
     * "그 슬러그는 있다"가 새어나간다(기존 여행 모듈과 같은 판단).
     */
    private Shortlink getOwned(Long memberId, String slug) {
        return shortlinkRepository
                .findBySlugAndMemberIdAndDeletedAtIsNull(SlugPolicy.normalize(slug), memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.SHORTLINK_NOT_FOUND));
    }

    private void applyExpiresAt(Shortlink link, JsonNode node) {
        if (node == null) {
            return;
        }
        link.updateExpiresAt(node.isNull() ? null : parseInstant(node.asString()));
    }

    private void applyPassword(Shortlink link, JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isNull()) {
            link.updatePasswordHash(null);
            return;
        }
        String raw = node.asString();
        if (!hasText(raw)) {
            throw new CustomException(ErrorCode.BAD_REQUEST, "비밀번호를 입력해 주세요.");
        }
        link.updatePasswordHash(passwordEncoder.encode(raw));
    }

    private Instant parseInstant(String text) {
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException nested) {
                throw new CustomException(ErrorCode.BAD_REQUEST, "만료일 형식이 올바르지 않습니다.");
            }
        }
    }

    /** 태그는 통째로 갈아끼운다. 부분 수정을 지원하지 않는 대신 화면이 보내는 것이 곧 결과다. */
    private List<String> replaceTags(Long shortlinkId, List<String> requested) {
        tagRepository.deleteByShortlinkId(shortlinkId);
        List<String> names = normalizeTags(requested);
        if (!names.isEmpty()) {
            tagRepository.saveAll(names.stream()
                    .map(name -> new ShortlinkTag(shortlinkId, name))
                    .toList());
        }
        return names;
    }

    private List<String> normalizeTags(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        // 순서를 지키면서 중복만 걷어낸다 — 화면이 보낸 칩 순서가 그대로 보여야 한다.
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String raw : requested) {
            String name = raw == null ? "" : raw.strip();
            if (name.isEmpty()) {
                continue;
            }
            if (name.length() > MAX_TAG_LENGTH) {
                throw new CustomException(ErrorCode.BAD_REQUEST, "태그는 50자를 넘을 수 없습니다.");
            }
            names.add(name);
        }
        return new ArrayList<>(names);
    }

    private Map<Long, List<String>> loadTags(List<Shortlink> links) {
        if (links.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = links.stream().map(Shortlink::getId).toList();
        return tagRepository.findAllByShortlinkIdIn(ids).stream()
                .collect(Collectors.groupingBy(ShortlinkTag::getShortlinkId,
                        Collectors.mapping(ShortlinkTag::getName, Collectors.toList())));
    }

    private ShortlinkDetail toDetail(Shortlink link) {
        List<String> tags = tagRepository.findAllByShortlinkIdOrderByIdAsc(link.getId()).stream()
                .map(ShortlinkTag::getName)
                .toList();
        List<TargetHistoryEntry> history = historyRepository
                .findAllByShortlinkIdOrderByChangedAtDescIdDesc(link.getId()).stream()
                .map(row -> new TargetHistoryEntry(row.getTargetUrl(), row.getReason(), row.getChangedAt()))
                .toList();
        OgPreview og = link.getOgTitle() == null && link.getOgImageUrl() == null
                ? null
                : new OgPreview(link.getOgTitle(), link.getOgImageUrl());

        return ShortlinkDetail.of(
                toSummary(link, tags, visitStatsService.visitTotal(link.getId()),
                        visitStatsService.lastVisit(link.getId()), clock.instant()),
                link.getCreatedAt(), link.getExpiresAt(), og, history);
    }

    private LinkSummary toSummary(Shortlink link, Map<Long, List<String>> tagsByLink,
                                  Map<Long, Long> visitTotals, Map<Long, Instant> lastVisits,
                                  Instant now) {
        return toSummary(link, tagsByLink.getOrDefault(link.getId(), List.of()),
                visitTotals.getOrDefault(link.getId(), 0L), lastVisits.get(link.getId()), now);
    }

    private LinkSummary toSummary(Shortlink link, List<String> tags,
                                  long visitCount, Instant lastVisitedAt, Instant now) {
        return new LinkSummary(
                link.getSlug(),
                properties.shortUrl(link.getSlug()),
                link.getTargetUrl(),
                link.getMemo(),
                tags,
                link.isCustomSlug(),
                link.isFavorite(),
                stateOf(link, now),
                link.hasPassword(),
                visitCount,
                lastVisitedAt);
    }

    /**
     * 화면 상태 파생(데이터 모델 §3). <b>만료가 비활성보다 먼저다</b> — 꺼 둔 링크의 만료일이
     * 지나면 화면에는 「만료」로 보인다. 방문자에게는 어차피 둘 다 같은 404다.
     */
    private LinkState stateOf(Shortlink link, Instant now) {
        if (link.isExpiredAt(now)) {
            return LinkState.EXPIRED;
        }
        return link.getStatus() == ShortlinkStatus.DISABLED ? LinkState.DISABLED : LinkState.ACTIVE;
    }

    private boolean matches(ListStatusFilter filter, LinkState state) {
        return switch (filter) {
            case ALL -> true;
            case ACTIVE -> state == LinkState.ACTIVE;
            case INACTIVE -> state != LinkState.ACTIVE;
        };
    }

    private String likePattern(String query) {
        return hasText(query) ? "%" + query.strip().toLowerCase(Locale.ROOT) + "%" : null;
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value.strip() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
