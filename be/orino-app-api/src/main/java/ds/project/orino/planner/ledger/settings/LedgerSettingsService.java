package ds.project.orino.planner.ledger.settings;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.domain.planner.ledger.entity.LedgerSettings;
import ds.project.orino.domain.planner.ledger.repository.LedgerAssetRepository;
import ds.project.orino.planner.ledger.common.LedgerBootstrap;
import ds.project.orino.planner.ledger.settings.dto.SettingsDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가계부 설정.
 *
 * <p>{@code monthStartDay}는 <b>예산 기간에만</b> 쓴다(확정 명세 §9). 카드 정산 사이클과
 * 정기 항목 주기는 여기에 영향받지 않는다 — 하나를 고치면 다른 것도 따라 움직이는 설정은
 * 사용자가 결과를 예측할 수 없다.
 */
@Service
public class LedgerSettingsService {

    /** 1~28은 모든 달에 있는 날짜다. 그 밖에 허용되는 값은 「말일」 하나뿐이다. */
    private static final int MAX_SAFE_DAY = 28;

    private final LedgerBootstrap bootstrap;
    private final LedgerAssetRepository assetRepository;

    public LedgerSettingsService(LedgerBootstrap bootstrap, LedgerAssetRepository assetRepository) {
        this.bootstrap = bootstrap;
        this.assetRepository = assetRepository;
    }

    @Transactional
    public SettingsDtos.View get(Long memberId) {
        return SettingsDtos.View.of(bootstrap.ensureSettings(memberId));
    }

    @Transactional
    public SettingsDtos.View update(Long memberId, SettingsDtos.Update request) {
        LedgerSettings settings = bootstrap.ensureSettings(memberId);

        if (request.monthStartDay() != null) {
            int day = request.monthStartDay();
            if (day > MAX_SAFE_DAY && day != LedgerSettings.LAST_DAY_OF_MONTH) {
                // 「30일 시작」은 2월에 존재하지 않는다. 말일을 원하면 99를 쓴다.
                throw new CustomException(ErrorCode.BAD_REQUEST,
                        "월 시작일은 1~28 또는 99(말일)만 쓸 수 있습니다.");
            }
            settings.updateMonthStartDay(day);
        }
        if (request.monthStartWeekendPolicy() != null) {
            settings.updateMonthStartWeekendPolicy(request.monthStartWeekendPolicy());
        }
        if (Boolean.TRUE.equals(request.clearDefaultAsset())) {
            settings.updateDefaultAssetId(null);
        } else if (request.defaultAssetId() != null) {
            assetRepository.findByIdAndMemberId(request.defaultAssetId(), memberId)
                    .orElseThrow(() -> new CustomException(ErrorCode.LEDGER_ASSET_NOT_FOUND));
            settings.updateDefaultAssetId(request.defaultAssetId());
        }
        if (request.defaultPerspective() != null) {
            settings.updateDefaultPerspective(request.defaultPerspective());
        }
        return SettingsDtos.View.of(settings);
    }
}
