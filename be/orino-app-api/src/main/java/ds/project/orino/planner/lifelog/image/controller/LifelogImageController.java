package ds.project.orino.planner.lifelog.image.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.lifelog.image.dto.LifelogImageUploadUrlRequest;
import ds.project.orino.planner.lifelog.image.dto.LifelogImageUploadUrlResponse;
import ds.project.orino.planner.lifelog.image.service.LifelogImageStorageService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lifelog/images")
public class LifelogImageController {

    private final LifelogImageStorageService imageStorageService;

    public LifelogImageController(LifelogImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    /**
     * 일상기록 사진(원본/썸네일) 업로드용 presigned PUT URL을 발급한다.
     * 브라우저는 uploadUrl로 이미지를 직접 PUT 한 뒤 objectKey를 moment 생성 요청에 실어 보낸다.
     */
    @PostMapping("/upload-url")
    public ApiResponse<LifelogImageUploadUrlResponse> createUploadUrl(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody LifelogImageUploadUrlRequest request) {
        return ApiResponse.success(
                imageStorageService.createUploadUrl(memberId, request.contentType(), request.kind()));
    }
}
