package ds.project.orino.planner.ledger.common;

import ds.project.orino.domain.planner.ledger.entity.LedgerAsset;
import ds.project.orino.domain.planner.ledger.entity.LedgerCategory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 자산·카테고리 이름 사전. 거래 한 줄마다 이름을 다시 조회하면 목록 한 번에 수백 질의가 난다.
 *
 * <p>이름이 없을 수 있다 — 미분류 거래의 카테고리가 그렇다. 그때 {@code null}을 그대로
 * 돌려준다. 「미분류」 같은 문구는 화면이 정할 일이지 서버가 박아 넣을 값이 아니다.
 */
public final class LedgerNames {

    private final Map<Long, String> assetNames = new HashMap<>();
    private final Map<Long, String> categoryNames = new HashMap<>();

    public LedgerNames(List<LedgerAsset> assets, List<LedgerCategory> categories) {
        for (LedgerAsset asset : assets) {
            assetNames.put(asset.getId(), asset.getName());
        }
        for (LedgerCategory category : categories) {
            categoryNames.put(category.getId(), category.getName());
        }
    }

    public String assetName(Long assetId) {
        return assetId == null ? null : assetNames.get(assetId);
    }

    public String categoryName(Long categoryId) {
        return categoryId == null ? null : categoryNames.get(categoryId);
    }
}
