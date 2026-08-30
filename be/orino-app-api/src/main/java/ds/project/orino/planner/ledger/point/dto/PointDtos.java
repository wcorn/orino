package ds.project.orino.planner.ledger.point.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 포인트·마일리지 입출력(`LDG-006`). */
public final class PointDtos {

    private PointDtos() {
    }

    /**
     * @param daysLeft     소멸까지 남은 날. 소멸일이 없으면 {@code null}, 지났으면 음수다
     * @param expiringSoon 곧 사라지는가. <b>서버가 판단한다</b> — 화면이 세면 시계가 둘이 된다
     */
    public record View(
            Long id,
            String name,
            String unit,
            long balance,
            LocalDate expiresOn,
            Integer daysLeft,
            boolean expiringSoon,
            String memo,
            int displayOrder
    ) {
    }

    public record SaveRequest(
            @NotBlank @Size(max = 60) String name,
            // 단위를 필수로 둔다. 「포인트」와 「마일」을 구분하지 않으면 두 줄을 더하고 싶어진다.
            @NotBlank @Size(max = 20) String unit,
            @PositiveOrZero Long balance,
            LocalDate expiresOn,
            @Size(max = 255) String memo,
            Integer displayOrder
    ) {
    }

    /**
     * @param clearExpiry 소멸일을 지운다. 안 보내는 것(그대로 두기)과 구별해야 해서 따로 둔다
     */
    public record UpdateRequest(
            @Size(max = 60) String name,
            @Size(max = 20) String unit,
            @PositiveOrZero Long balance,
            LocalDate expiresOn,
            Boolean clearExpiry,
            @Size(max = 255) String memo,
            Integer displayOrder
    ) {
    }
}
