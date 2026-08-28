package ds.project.orino.planner.ledger.fx;

import ds.project.orino.common.exception.CustomException;
import ds.project.orino.common.exception.ErrorCode;
import ds.project.orino.planner.travel.tools.dto.ExchangeRateResponse;
import ds.project.orino.planner.travel.tools.service.ExchangeRateService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 가계부가 쓰는 환율 조회. {@link ExchangeRateService}(여행·도구)를 <b>그대로 참조한다</b> —
 * ECB 고시는 무료라 #1148이 세운 하드캡에 새 항목이 붙지 않는다(D-3).
 *
 * <p>이 클래스가 존재하는 이유는 <b>실패를 다르게 다루기</b> 위해서다.
 * <ul>
 *   <li>고시표에 없는 통화는 <b>사용자 입력이 틀린 것</b>이다 → {@code LDG-ERR-020} (400)</li>
 *   <li>ECB에 닿지 못한 것은 <b>사용자 잘못이 아니다</b> → 빈 값을 돌려준다. 원장은
 *       환율 때문에 기록을 막지 않는다 — 직접 입력하거나 원화로만 적으면 된다(확정 명세 §11.1)</li>
 * </ul>
 *
 * <p><b>과거 날짜의 고시는 가져올 수 없다.</b> ECB 클라이언트는 최신 고시표 한 벌만 준다.
 * 그래서 {@code on}은 <b>어떤 거래의 환율인지</b>를 알리는 값일 뿐이고, 실제로 쓰는 값은
 * 최신 고시(그 {@code referenceDate}는 응답에 담아 돌려준다)다. 이 한계가 원장을 해치지는
 * 않는다 — 무엇을 썼든 <b>저장하는 순간 고정</b>되고 다시는 재계산하지 않기 때문이다.
 */
@Service
public class LedgerFxService {

    /** 원장에 남는 값은 언제나 원화다. 환산의 도착 통화는 고정이다. */
    public static final String KRW = "KRW";

    private final ExchangeRateService exchangeRateService;

    public LedgerFxService(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    /**
     * 조회용. 못 가져왔으면 {@code rate}가 {@code null}인 응답을 준다 —
     * 화면은 그때 직접 입력 칸을 열면 된다. 에러로 올리면 입력이 막힌다.
     */
    public LedgerFxRateResponse lookup(String currency, LocalDate on) {
        String code = normalize(currency);
        if (KRW.equals(code)) {
            // 원화를 원화로 환산할 일은 없다. 1을 주는 게 정직하고, 화면도 그렇게 읽는다.
            return new LedgerFxRateResponse(code, BigDecimal.ONE, on, ExchangeRateResponse.SOURCE);
        }
        return fetch(code)
                .map(res -> new LedgerFxRateResponse(
                        code, res.rate(), res.referenceDate(), res.source()))
                .orElseGet(() -> new LedgerFxRateResponse(code, null, null,
                        ExchangeRateResponse.SOURCE));
    }

    /**
     * 거래 저장에 쓸 환율. 비어 있으면 <b>호출자가 원화로만 적도록</b> 넘어가야 한다 —
     * 여기서 예외를 던지면 ECB가 잠깐 죽었다는 이유로 기록이 막힌다.
     */
    public Optional<BigDecimal> resolveRate(String currency) {
        String code = normalize(currency);
        if (KRW.equals(code)) {
            return Optional.of(BigDecimal.ONE);
        }
        return fetch(code).map(ExchangeRateResponse::rate);
    }

    private Optional<ExchangeRateResponse> fetch(String currency) {
        try {
            return Optional.of(exchangeRateService.rate(currency, KRW));
        } catch (CustomException e) {
            if (e.getErrorCode() == ErrorCode.TRAVEL_FX_UNSUPPORTED_CURRENCY) {
                // 고시표는 받아 왔는데 그 통화가 없다 — 사용자가 고쳐야 할 값이다.
                throw new CustomException(ErrorCode.LEDGER_FX_UNSUPPORTED_CURRENCY);
            }
            // 고시표 자체를 못 받았다. 원장은 이것 때문에 멈추지 않는다.
            return Optional.empty();
        }
    }

    private String normalize(String currency) {
        return currency == null ? null : currency.trim().toUpperCase();
    }
}
