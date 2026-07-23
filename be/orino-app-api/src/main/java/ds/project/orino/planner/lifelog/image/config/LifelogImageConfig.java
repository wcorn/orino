package ds.project.orino.planner.lifelog.image.config;

import ds.project.orino.planner.image.config.ImageStorageProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * 일상기록 사진 삭제용 {@link S3Client} 빈.
 * <p>
 * 노트 이미지 파이프라인은 presigned URL만 발급해 바이트를 BE가 거치지 않지만, 일상기록은
 * moment/사진 삭제 시 MinIO 오브젝트를 best-effort로 지워야 해서 서버측 클라이언트가 필요하다.
 * 발급 설정({@link ImageStorageProperties})은 노트 이미지와 공유한다(같은 MinIO·버킷).
 */
@Configuration
public class LifelogImageConfig {

    @Bean
    public S3Client lifelogImageS3Client(ImageStorageProperties props) {
        return S3Client.builder()
                .region(Region.of(props.region()))
                .endpointOverride(URI.create(props.endpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
