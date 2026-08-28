package ds.project.orino.planner.ledger.receipt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 영수증 첨부 DTO 묶음. */
public final class LedgerReceiptDtos {

    private LedgerReceiptDtos() {
    }

    public record UploadUrlRequest(@NotBlank String contentType) {
    }

    /**
     * @param uploadUrl 브라우저가 바이트를 직접 PUT 할 주소(만료 있음)
     * @param objectKey 업로드가 끝난 뒤 {@link AttachRequest}로 다시 보낼 키
     */
    public record UploadUrl(String uploadUrl, String publicUrl, String objectKey) {
    }

    public record AttachRequest(
            @NotBlank @Size(max = 255) String objectKey,
            @Size(max = 60) String contentType,
            Long byteSize
    ) {
    }

    public record View(
            Long id,
            String objectKey,
            String url,
            String contentType,
            Long byteSize,
            int displayOrder
    ) {
    }
}
