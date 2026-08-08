package ds.project.orino.planner.travel.photo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 업로드가 끝난 사진의 메타 등록.
 *
 * <p><b>성공한 파일만 보낸다.</b> 실패한 장은 FE가 재시도 목록에 남긴다 — 한 장이 실패했다고
 * 이미 올라간 아홉 장을 버리게 하지 않는다(§2.5).
 */
public record PhotoRegisterRequest(

        @NotEmpty
        @Valid
        List<Photo> photos
) {

    public record Photo(

            @NotBlank
            @Size(max = 512)
            String objectKey,

            /** 썸네일만 실패할 수 있다. null이면 화면이 원본을 줄여 쓴다. */
            @Size(max = 512)
            String thumbKey,

            Integer width,
            Integer height
    ) {
    }
}
