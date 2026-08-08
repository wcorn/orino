package ds.project.orino.planner.travel.photo.controller;

import ds.project.orino.common.response.ApiResponse;
import ds.project.orino.planner.travel.photo.dto.PhotoRegisterRequest;
import ds.project.orino.planner.travel.photo.dto.PhotoResponse;
import ds.project.orino.planner.travel.photo.dto.PhotoUploadUrlRequest;
import ds.project.orino.planner.travel.photo.dto.PhotoUploadUrlResponse;
import ds.project.orino.planner.travel.photo.service.TravelPhotoService;
import ds.project.orino.planner.travel.photo.service.TravelPhotoStorageService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/travel")
public class TravelPhotoController {

    private final TravelPhotoService photoService;
    private final TravelPhotoStorageService storageService;

    public TravelPhotoController(TravelPhotoService photoService,
                                 TravelPhotoStorageService storageService) {
        this.photoService = photoService;
        this.storageService = storageService;
    }

    /**
     * presigned PUT URL 발급. 브라우저가 이 URL로 바이트를 직접 올린 뒤 {@code objectKey}를
     * 메타 등록에 실어 보낸다.
     */
    @PostMapping("/activities/{activityId}/photos/upload-url")
    public ApiResponse<PhotoUploadUrlResponse> createUploadUrl(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long activityId,
            @Valid @RequestBody PhotoUploadUrlRequest request) {
        // 남의 일정 경로에 올리거나, 시작 전이라 어차피 거부될 사진을 올리게 하지 않는다.
        photoService.requireUploadable(memberId, activityId);
        return ApiResponse.success(
                storageService.createUploadUrl(activityId, request.contentType(), request.kind()));
    }

    /** 업로드가 끝난 사진의 메타 등록. 성공한 장만 담겨 온다. */
    @PostMapping("/activities/{activityId}/photos")
    public ApiResponse<List<PhotoResponse>> register(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long activityId,
            @Valid @RequestBody PhotoRegisterRequest request) {
        return ApiResponse.success(photoService.register(memberId, activityId, request));
    }

    @DeleteMapping("/photos/{photoId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId,
                                    @PathVariable Long photoId) {
        photoService.delete(memberId, photoId);
        return ApiResponse.success();
    }
}
