package ds.project.orino.planner.image.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.image.dto.ImageUploadUrlRequest;
import ds.project.orino.planner.image.dto.ImageUploadUrlResponse;
import ds.project.orino.planner.image.service.ImageStorageService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planner/images")
public class ImageController {

    private final ImageStorageService imageStorageService;

    public ImageController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    /**
     * 노트 이미지 업로드용 presigned PUT URL을 발급한다.
     * 브라우저는 uploadUrl로 이미지를 직접 PUT 한 뒤 publicUrl을 노트에 삽입한다.
     */
    @PostMapping("/upload-url")
    public ApiResponse<ImageUploadUrlResponse> createUploadUrl(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody ImageUploadUrlRequest request) {
        return ApiResponse.success(
                imageStorageService.createUploadUrl(memberId, request.contentType()));
    }
}
