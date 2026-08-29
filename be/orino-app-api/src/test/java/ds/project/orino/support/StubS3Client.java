package ds.project.orino.support;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 메모리 위의 버킷. 보존 배치가 <b>무엇을 훑고 무엇을 지웠는지</b>만 본다.
 *
 * <p>{@link S3Client}의 나머지 오퍼레이션은 기본 구현이 그대로 남아 부르면 터진다 — 배치가
 * 목록과 삭제 말고 다른 일을 하기 시작하면 테스트가 먼저 알려준다.
 */
public class StubS3Client implements S3Client {

    private final Map<String, Instant> objects = new LinkedHashMap<>();

    /** 오브젝트 하나를 놓는다. {@code lastModified}가 유예 기간 판정의 기준이다. */
    public void put(String key, Instant lastModified) {
        objects.put(key, lastModified);
    }

    public boolean has(String key) {
        return objects.containsKey(key);
    }

    public void clear() {
        objects.clear();
    }

    @Override
    public ListObjectsV2Response listObjectsV2(ListObjectsV2Request request) {
        List<S3Object> contents = new ArrayList<>();
        for (Map.Entry<String, Instant> entry : objects.entrySet()) {
            if (entry.getKey().startsWith(request.prefix())) {
                contents.add(S3Object.builder()
                        .key(entry.getKey())
                        .lastModified(entry.getValue())
                        .build());
            }
        }
        // 한 페이지에 다 담는다 — 페이지네이션 자체는 SDK의 몫이라 여기서 흉내 낼 것이 없다.
        return ListObjectsV2Response.builder()
                .contents(contents)
                .isTruncated(false)
                .build();
    }

    @Override
    public DeleteObjectsResponse deleteObjects(DeleteObjectsRequest request) {
        for (ObjectIdentifier identifier : request.delete().objects()) {
            objects.remove(identifier.key());
        }
        return DeleteObjectsResponse.builder().build();
    }

    @Override
    public String serviceName() {
        return "s3";
    }

    @Override
    public void close() {
        // 메모리라 닫을 것이 없다.
    }
}
