package ds.project.orino.planner.shortlink;

import ds.project.orino.domain.planner.shortlink.entity.VisitDevice;
import ds.project.orino.planner.shortlink.visit.Referrers;
import ds.project.orino.planner.shortlink.visit.UserAgents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 방문 판정(명세 §8.1·§8.2). 저장 전에 원문을 버리기 위한 순수 로직이다.
 */
class UserAgentsTest {

    private static final String IPHONE_SAFARI =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
                    + "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
    private static final String MAC_CHROME =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Nested
    @DisplayName("봇 판정")
    class BotDetection {

        @ParameterizedTest
        @ValueSource(strings = {
                "kakaotalk-scrap/1.0",
                "facebookexternalhit/1.1",
                "Slackbot-LinkExpanding 1.0",
                "Discordbot/2.0",
                "TelegramBot (like TwitterBot)",
                "WhatsApp/2.23",
                "Twitterbot/1.0",
                "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
                "Mozilla/5.0 (compatible; bingbot/2.0)",
                // 이름을 모르는 것들도 일반 패턴에 걸린다.
                "some-new-messenger-preview/1.0",
                "custom crawler",
                "python-requests spider"
        })
        @DisplayName("프리뷰·크롤러는 봇으로 센다 — 사람이 누르기 전에 먼저 열기 때문이다")
        void detectsBots(String userAgent) {
            assertThat(UserAgents.isBot(userAgent)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {IPHONE_SAFARI, MAC_CHROME})
        @DisplayName("보통의 브라우저는 사람으로 센다")
        void detectsHumans(String userAgent) {
            assertThat(UserAgents.isBot(userAgent)).isFalse();
        }

        @Test
        @DisplayName("User-Agent가 없으면 봇으로 센다 — 브라우저가 아니다")
        void treatsMissingUserAgentAsBot() {
            assertThat(UserAgents.isBot(null)).isTrue();
            assertThat(UserAgents.isBot("")).isTrue();
        }
    }

    @Nested
    @DisplayName("기기 판정")
    class DeviceDetection {

        @Test
        @DisplayName("아이폰은 모바일, 맥은 데스크탑")
        void detectsMobileAndDesktop() {
            assertThat(UserAgents.deviceOf(IPHONE_SAFARI)).isEqualTo(VisitDevice.MOBILE);
            assertThat(UserAgents.deviceOf(MAC_CHROME)).isEqualTo(VisitDevice.DESKTOP);
        }

        @Test
        @DisplayName("아이패드와 안드로이드 태블릿은 태블릿이다 — mobile 유무로 갈린다")
        void detectsTablets() {
            assertThat(UserAgents.deviceOf(
                    "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) Mobile/15E148"))
                    .isEqualTo(VisitDevice.TABLET);
            assertThat(UserAgents.deviceOf(
                    "Mozilla/5.0 (Linux; Android 14; SM-X710) AppleWebKit/537.36"))
                    .isEqualTo(VisitDevice.TABLET);
            assertThat(UserAgents.deviceOf(
                    "Mozilla/5.0 (Linux; Android 14; SM-S921N Mobile) AppleWebKit/537.36"))
                    .isEqualTo(VisitDevice.MOBILE);
        }

        @Test
        @DisplayName("모르면 UNKNOWN이다 — 아무 쪽으로도 밀어 넣지 않는다")
        void fallsBackToUnknown() {
            assertThat(UserAgents.deviceOf("curl/8.4.0")).isEqualTo(VisitDevice.UNKNOWN);
            assertThat(UserAgents.deviceOf(null)).isEqualTo(VisitDevice.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("리퍼러")
    class ReferrerDomain {

        @Test
        @DisplayName("전체 URL을 줘도 도메인만 남는다 — 경로·쿼리는 저장되지 않는다")
        void keepsDomainOnly() {
            assertThat(Referrers.domainOf(
                    "https://mail.google.com/mail/u/0/?tab=rm#inbox/FMfcgz"))
                    .isEqualTo("mail.google.com");
            assertThat(Referrers.domainOf("https://Blog.Example.com/post/12?utm=x"))
                    .isEqualTo("blog.example.com");
        }

        @Test
        @DisplayName("없거나 읽을 수 없으면 null이다 — 원문을 대신 저장하지 않는다")
        void returnsNullWhenUnreadable() {
            assertThat(Referrers.domainOf(null)).isNull();
            assertThat(Referrers.domainOf("")).isNull();
            assertThat(Referrers.domainOf("about:blank")).isNull();
            assertThat(Referrers.domainOf("h t t p://broken")).isNull();
        }
    }
}
