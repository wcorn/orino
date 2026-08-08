package ds.project.orino.planner.travel.push.crypto;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;

/**
 * P-256(secp256r1) 키를 웹푸시가 쓰는 <b>원시 바이트 형태</b>와 JDK 키 객체 사이에서 옮긴다.
 *
 * <p>브라우저가 주는 구독 키({@code p256dh})와 VAPID 키는 X.509/PKCS#8이 아니라
 * 65바이트 비압축 점({@code 0x04 || X || Y})과 32바이트 스칼라다. JDK는 이 형태를 직접
 * 받아주지 않아 좌표를 꺼내 다시 만들어야 한다.
 *
 * <p>BouncyCastle을 쓰지 않는다 — 필요한 것이 전부 JDK에 있고, 의존성을 늘릴 이유가 없다.
 */
public final class P256 {

    private static final String CURVE = "secp256r1";
    /** 비압축 점 표시(SEC1). 압축 점은 브라우저가 주지 않는다. */
    private static final byte UNCOMPRESSED = 0x04;
    private static final int COORDINATE_BYTES = 32;
    private static final int PUBLIC_KEY_BYTES = 1 + COORDINATE_BYTES * 2;

    private P256() {
    }

    public static ECParameterSpec parameterSpec() {
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec(CURVE));
            return params.getParameterSpec(ECParameterSpec.class);
        } catch (Exception e) {
            throw new IllegalStateException("P-256 파라미터를 만들 수 없습니다.", e);
        }
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec(CURVE));
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("P-256 키쌍을 만들 수 없습니다.", e);
        }
    }

    /** 65바이트 비압축 점 → 공개키. */
    public static PublicKey publicKey(byte[] uncompressedPoint) {
        if (uncompressedPoint.length != PUBLIC_KEY_BYTES
                || uncompressedPoint[0] != UNCOMPRESSED) {
            throw new IllegalArgumentException(
                    "P-256 공개키는 0x04로 시작하는 65바이트여야 합니다.");
        }
        BigInteger x = new BigInteger(1,
                Arrays.copyOfRange(uncompressedPoint, 1, 1 + COORDINATE_BYTES));
        BigInteger y = new BigInteger(1,
                Arrays.copyOfRange(uncompressedPoint, 1 + COORDINATE_BYTES, PUBLIC_KEY_BYTES));
        try {
            return KeyFactory.getInstance("EC")
                    .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), parameterSpec()));
        } catch (Exception e) {
            throw new IllegalArgumentException("P-256 공개키를 읽을 수 없습니다.", e);
        }
    }

    /** 32바이트 스칼라 → 개인키. */
    public static PrivateKey privateKey(byte[] scalar) {
        try {
            return KeyFactory.getInstance("EC").generatePrivate(
                    new ECPrivateKeySpec(new BigInteger(1, scalar), parameterSpec()));
        } catch (Exception e) {
            throw new IllegalArgumentException("P-256 개인키를 읽을 수 없습니다.", e);
        }
    }

    /** 공개키 → 65바이트 비압축 점. 앞을 0으로 채워 좌표 길이를 고정한다. */
    public static byte[] encode(PublicKey publicKey) {
        ECPoint point = ((java.security.interfaces.ECPublicKey) publicKey).getW();
        byte[] encoded = new byte[PUBLIC_KEY_BYTES];
        encoded[0] = UNCOMPRESSED;
        writeCoordinate(point.getAffineX(), encoded, 1);
        writeCoordinate(point.getAffineY(), encoded, 1 + COORDINATE_BYTES);
        return encoded;
    }

    /** 개인키 → 32바이트 스칼라. */
    public static byte[] encode(PrivateKey privateKey) {
        BigInteger s = ((java.security.interfaces.ECPrivateKey) privateKey).getS();
        byte[] encoded = new byte[COORDINATE_BYTES];
        writeCoordinate(s, encoded, 0);
        return encoded;
    }

    /**
     * BigInteger는 부호 바이트가 붙거나 앞의 0이 잘려 길이가 32가 아닐 수 있다.
     * 좌표는 <b>고정 길이</b>여야 해서 오른쪽 정렬로 채운다.
     */
    private static void writeCoordinate(BigInteger value, byte[] target, int offset) {
        byte[] bytes = value.toByteArray();
        int length = Math.min(bytes.length, COORDINATE_BYTES);
        System.arraycopy(bytes, bytes.length - length, target,
                offset + COORDINATE_BYTES - length, length);
    }
}
