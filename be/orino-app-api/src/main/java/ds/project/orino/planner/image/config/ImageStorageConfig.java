package ds.project.orino.planner.image.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * MinIO presigned URL 발급용 S3Presigner 빈.
 * <p>
 * endpoint를 공개 주소(img.orino.dev)로 설정해, 서명된 URL을 브라우저가 그대로
 * PUT/GET 할 수 있게 한다. path-style(버킷을 경로로)로 강제해 MinIO와 호환한다.
 */
@Configuration
@EnableConfigurationProperties(ImageStorageProperties.class)
public class ImageStorageConfig {

    /**
     * 서버가 직접 버킷을 훑고 지우기 위한 클라이언트. presigner는 서명만 하고 호출은 못 한다.
     *
     * <p>지금 쓰는 곳은 <b>영수증 보존 배치 하나</b>다(#1275). 업로드 바이트는 여전히 BE를
     * 거치지 않는다 — 브라우저가 presigned URL로 MinIO에 직접 올린다.
     */
    @Bean
    public S3Client imageS3Client(ImageStorageProperties props) {
        return S3Client.builder()
                .region(Region.of(props.region()))
                .endpointOverride(URI.create(props.endpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    @Bean
    public S3Presigner imageS3Presigner(ImageStorageProperties props) {
        return S3Presigner.builder()
                .region(Region.of(props.region()))
                .endpointOverride(URI.create(props.endpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
