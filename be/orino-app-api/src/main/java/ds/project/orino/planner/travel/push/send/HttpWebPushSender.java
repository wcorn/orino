package ds.project.orino.planner.travel.push.send;

import ds.project.orino.domain.planner.push.entity.PushSubscription;
import ds.project.orino.planner.travel.push.config.VapidProperties;
import ds.project.orino.planner.travel.push.crypto.P256;
import ds.project.orino.planner.travel.push.crypto.VapidSigner;
import ds.project.orino.planner.travel.push.crypto.WebPushEncryption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

/**
 * 실제 발송. 페이로드를 종단 암호화(#1073)하고 VAPID로 서명해 푸시 서비스에 POST한다.
 *
 * <p>JDK {@link HttpClient}만 쓴다 — 라이브러리를 안 쓰기로 한 이유가 여기서도 같다.
 * 요청 하나에 필요한 것은 헤더 몇 개와 바이트 배열이다.
 */
@Component
public class HttpWebPushSender implements WebPushSender {

    private static final Logger log = LoggerFactory.getLogger(HttpWebPushSender.class);

    /** 레코드 크기. 한 레코드로 보내므로 페이로드보다 넉넉해야 한다. */
    private static final int RECORD_SIZE = 4096;
    /** 푸시 서비스가 알림을 보관할 시간(초). 지나면 버린다 — 지난 일정 알림은 의미가 없다. */
    private static final int TTL_SECONDS = 3600;

    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final HttpClient httpClient;
    private final VapidProperties props;
    private final Clock clock;

    public HttpWebPushSender(VapidProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public Result send(PushSubscription subscription, String payloadJson) {
        if (!props.enabled()) {
            return Result.failed("VAPID 키가 없습니다.");
        }
        try {
            byte[] body = WebPushEncryption.encrypt(
                    DECODER.decode(subscription.getP256dh()),
                    DECODER.decode(subscription.getAuth()),
                    payloadJson.getBytes(StandardCharsets.UTF_8),
                    RECORD_SIZE);

            HttpResponse<String> response = httpClient.send(
                    request(subscription.getEndpoint(), body),
                    HttpResponse.BodyHandlers.ofString());

            return interpret(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.failed("발송이 중단되었습니다.");
        } catch (Exception e) {
            log.warn("웹푸시 발송 실패: {}", e.getMessage());
            return Result.failed(e.getMessage());
        }
    }

    private HttpRequest request(String endpoint, byte[] body) {
        PrivateKey privateKey = P256.privateKey(DECODER.decode(props.privateKey()));
        byte[] publicKey = DECODER.decode(props.publicKey());

        return HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", VapidSigner.authorizationHeader(
                        endpoint, props.subject(), publicKey, privateKey, clock.instant()))
                // 본문은 이미 암호화·인코딩되어 있다.
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("TTL", String.valueOf(TTL_SECONDS))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
    }

    /**
     * 404·410은 <b>구독이 죽은 것</b>이라 지워야 한다(§6). 그 외 실패는 일시적일 수 있어
     * 구독을 남긴다 — 잘못 지우면 사용자가 다시 구독해야 알림이 온다.
     */
    private static Result interpret(int status, String body) {
        if (status >= 200 && status < 300) {
            return Result.ok();
        }
        String reason = "HTTP " + status + (body == null || body.isBlank() ? "" : ": " + body);
        return status == 404 || status == 410 ? Result.gone(reason) : Result.failed(reason);
    }
}
