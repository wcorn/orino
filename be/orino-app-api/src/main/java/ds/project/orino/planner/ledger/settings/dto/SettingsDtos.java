package ds.project.orino.planner.ledger.settings.dto;

import ds.project.orino.domain.planner.ledger.entity.LedgerMonthStartWeekendPolicy;
import ds.project.orino.domain.planner.ledger.entity.LedgerPerspective;
import ds.project.orino.domain.planner.ledger.entity.LedgerSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 설정 DTO 묶음. */
public final class SettingsDtos {

    private SettingsDtos() {
    }

    /**
     * 설정 수정. 보낸 것만 바꾼다.
     *
     * @param monthStartDay 1~28 또는 99(말일). 29~31을 허용하지 않는 이유는 2월이다 —
     *                      「30일 시작」은 어떤 달에는 존재하지 않는 날짜다
     */
    public record Update(
            @Min(1) @Max(99) Integer monthStartDay,
            LedgerMonthStartWeekendPolicy monthStartWeekendPolicy,
            Long defaultAssetId,
            Boolean clearDefaultAsset,
            LedgerPerspective defaultPerspective
    ) {
    }

    public record View(
            int monthStartDay,
            LedgerMonthStartWeekendPolicy monthStartWeekendPolicy,
            Long defaultAssetId,
            LedgerPerspective defaultPerspective
    ) {

        public static View of(LedgerSettings settings) {
            return new View(settings.getMonthStartDay(), settings.getMonthStartWeekendPolicy(),
                    settings.getDefaultAssetId(), settings.getDefaultPerspective());
        }
    }
}
