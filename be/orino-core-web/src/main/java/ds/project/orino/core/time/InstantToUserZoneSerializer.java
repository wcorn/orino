package ds.project.orino.core.time;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * UTC로 저장된 {@link Instant}를 요청 사용자 시간대 기준의 offset 포함 ISO-8601로 직렬화한다.
 * <p>
 * 예: UTC {@code 2026-06-06T19:00:00Z} → 사용자 TZ가 {@code Asia/Seoul}이면
 * {@code "2026-06-07T04:00:00+09:00"}.
 * <p>
 * Spring Boot 4 / Jackson 3({@code tools.jackson}) 기반.
 */
public class InstantToUserZoneSerializer extends StdSerializer<Instant> {

    public InstantToUserZoneSerializer() {
        super(Instant.class);
    }

    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializationContext ctxt) {
        OffsetDateTime offset = value.atZone(UserTimeZone.get()).toOffsetDateTime();
        gen.writeString(offset.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }
}
