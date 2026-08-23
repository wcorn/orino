package ds.project.orino.planner.shortlink.dto;

/** 사이드바 태그 한 줄. 살아 있는 링크에 붙은 것만 센다. */
public record TagCount(String name, long count) {
}
