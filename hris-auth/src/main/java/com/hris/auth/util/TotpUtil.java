package com.hris.auth.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

/**
 * TOTP（RFC 6238）工具：HMAC-SHA1、6 位动态码、30 秒步长、±1 步容差。
 * 纯函数，无业务、无副作用。
 */
public final class TotpUtil {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int DIGITS = 6;
    private static final long STEP_SECONDS = 30;
    private static final int WINDOW = 1;

    private TotpUtil() {
    }

    /** 生成 20 字节随机密钥并 Base32 编码。 */
    public static String generateSecret() {
        byte[] seed = new byte[20];
        new SecureRandom().nextBytes(seed);
        return base32Encode(seed);
    }

    /** 校验动态码（±1 步容差，常量时间比较）。 */
    public static boolean verify(String base32Secret, String code) {
        if (base32Secret == null || code == null) {
            return false;
        }
        byte[] key = base32Decode(base32Secret);
        long counter = Instant.now().getEpochSecond() / STEP_SECONDS;
        for (int i = -WINDOW; i <= WINDOW; i++) {
            if (constantTimeEquals(code, generate(key, counter + i))) {
                return true;
            }
        }
        return false;
    }

    /** 生成 otpauth URI（供扫码）。 */
    public static String otpauthUrl(String secret, String issuer, String account) {
        return "otpauth://totp/" + issuer + ":" + account
                + "?secret=" + secret + "&issuer=" + issuer
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    private static String generate(byte[] key, long counter) {
        byte[] msg = new byte[8];
        for (int i = 7; i >= 0; i--) {
            msg[i] = (byte) (counter & 0xff);
            counter >>= 8;
        }
        byte[] hash = hmacSha1(key, msg);
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        int otp = binary % (int) Math.pow(10, DIGITS);
        return String.format(Locale.ROOT, "%0" + DIGITS + "d", otp);
    }

    private static byte[] hmacSha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA1 不可用", e);
        }
    }

    private static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1f));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1f));
        }
        return sb.toString();
    }

    private static byte[] base32Decode(String input) {
        String s = input.toUpperCase(Locale.ROOT).replaceAll("[=]", "");
        int buffer = 0;
        int bitsLeft = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (char c : s.toCharArray()) {
            int val = ALPHABET.indexOf(c);
            if (val < 0) {
                continue;
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
