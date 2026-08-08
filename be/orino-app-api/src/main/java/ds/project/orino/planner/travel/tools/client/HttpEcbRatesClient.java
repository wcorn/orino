package ds.project.orino.planner.travel.tools.client;

import ds.project.orino.planner.travel.tools.config.ToolsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * ECB 일일 고시(eurofxref-daily.xml). 무료·무인증이다.
 *
 * <p>XML이라 파서를 쓴다. <b>외부에서 받은 XML</b>이므로 DTD·외부 엔티티를 끈다 —
 * 켜 두면 XXE로 서버 파일을 읽히거나 내부망을 긁을 수 있다.
 */
@Component
public class HttpEcbRatesClient implements EcbRatesClient {

    private static final Logger log = LoggerFactory.getLogger(HttpEcbRatesClient.class);

    private final RestClient restClient;

    public HttpEcbRatesClient(ToolsProperties props) {
        this.restClient = RestClient.builder()
                .baseUrl(props.fxUrl())
                .requestFactory(requestFactory(props))
                .build();
    }

    private static ClientHttpRequestFactory requestFactory(ToolsProperties props) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        return factory;
    }

    @Override
    public Optional<EcbRates> latest() {
        try {
            String xml = restClient.get().retrieve().body(String.class);
            return xml == null ? Optional.empty() : parse(xml);
        } catch (Exception e) {
            log.warn("ECB 환율 조회 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 구조 —
     * {@code <Cube><Cube time='2026-08-07'><Cube currency='JPY' rate='182.64'/> ... }
     *
     * <p>날짜를 가진 {@code Cube}가 하루치 묶음이고 그 아래가 통화별 값이다.
     */
    private static Optional<EcbRates> parse(String xml) throws Exception {
        Document document = secureBuilderFactory().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));

        NodeList cubes = document.getElementsByTagName("Cube");
        LocalDate referenceDate = null;
        Map<String, BigDecimal> perEur = new LinkedHashMap<>();

        for (int i = 0; i < cubes.getLength(); i++) {
            Element cube = (Element) cubes.item(i);
            if (cube.hasAttribute("time")) {
                referenceDate = LocalDate.parse(cube.getAttribute("time"));
            } else if (cube.hasAttribute("currency") && cube.hasAttribute("rate")) {
                perEur.put(cube.getAttribute("currency"),
                        new BigDecimal(cube.getAttribute("rate")));
            }
        }
        return referenceDate == null || perEur.isEmpty()
                ? Optional.empty()
                : Optional.of(new EcbRates(referenceDate, Map.copyOf(perEur)));
    }

    /** XXE 차단. 외부에서 받은 XML을 파싱할 때 기본값은 안전하지 않다. */
    private static DocumentBuilderFactory secureBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }
}
