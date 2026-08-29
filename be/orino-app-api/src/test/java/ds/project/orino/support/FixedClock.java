package ds.project.orino.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 테스트 클래스가 도는 동안 시각을 못박는다.
 *
 * <p>애너테이션이지 설정이 아니다 — 스프링 컨텍스트를 가르지 않으므로 시각이 몇 개로
 * 갈리든 컨텍스트는 한 벌이다. {@link ApiTestSupport}가 각 테스트 전에 읽어서 적용하고,
 * 안 붙은 클래스는 실시각으로 되돌린다.
 *
 * <p>{@code @Nested} 안쪽 클래스는 바깥 클래스에 붙은 것을 따른다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FixedClock {

    /** 못박을 시각(ISO-8601 UTC). {@link TestInstants}의 상수를 쓴다. */
    String value() default TestInstants.FIXED_NOW;
}
