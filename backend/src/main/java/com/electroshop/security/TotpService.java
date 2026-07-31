package com.electroshop.security;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Minimal RFC 6238 TOTP (Time-based One-Time Password) implementation for
   * Admin 2FA (feature #6). Deliberately dependency-free (uses only
                                                            * javax.crypto/java.security, already used by {@link JwtService}) so it never
   * needs a new Maven dependency that can't be verified offline in this
   * environment — same HMAC machinery Google Authenticator / Microsoft
   * Authenticator / Authy expect (HMAC-SHA1, 6 digits, 30s step).
   */
@Service
  public class TotpService {

    private static final String ALGORITHM = "HmacSHA1";
        private static final int DIGITS = 6;
        private static final int STEP_SECONDS = 30;
        /** How many steps before/after "now" still validate — tolerates minor clock drift. */
    private static final int WINDOW = 1;
        private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    /** Generates a fresh random 20-byte (160-bit) secret, Base32-encoded. */
    public String generateSecret() {
              byte[] bytes = new byte[20];
              new SecureRandom().nextBytes(bytes);
              return base32Encode(bytes);
    }

    /** otpauth:// URI an authenticator app can import (typed in manually or via a QR the user generates from it). */
    public String buildOtpAuthUrl(String secret, String accountEmail) {
              String issuer = "ElectroShop";
              String label = issuer + ":" + accountEmail;
              return "otpauth://totp/" + urlEncode(label)
                                + "?secret=" + secret
                                + "&issuer=" + urlEncode(issuer)
                                + "&digits=" + DIGITS
                                + "&period=" + STEP_SECONDS
                                + "&algorithm=SHA1";
    }

    /** Verifies a 6-digit code against the secret, tolerating +/- one 30s step. */
    public boolean verifyCode(String secret, String code) {
              if (secret == null || secret.isBlank() || code == null || !code.matches("\\d{6}")) {
                            return false;
              }
              long currentStep = System.currentTimeMillis() / 1000L / STEP_SECONDS;
              byte[] key = base32Decode(secret);
              for (int i = -WINDOW; i <= WINDOW; i++) {
                            String candidate = generateCode(key, currentStep + i);
                            if (candidate.equals(code)) {
                                              return true;
                            }
              }
              return false;
    }

    private String generateCode(byte[] key, long step) {
              byte[] data = new byte[8];
              long value = step;
              for (int i = 7; i >= 0; i--) {
                            data[i] = (byte) (value & 0xFF);
                            value >>= 8;
              }
              try {
                            Mac mac = Mac.getInstance(ALGORITHM);
                            mac.init(new SecretKeySpec(key, ALGORITHM));
                            byte[] hash = mac.doFinal(data);
                            int offset = hash[hash.length - 1] & 0x0F;
                            int binary = ((hash[offset] & 0x7F) << 24)
                                                  | ((hash[offset + 1] & 0xFF) << 16)
                                                  | ((hash[offset + 2] & 0xFF) << 8)
                                                  | (hash[offset + 3] & 0xFF);
                            int otp = binary % 1_000_000;
                            return String.format(Locale.ROOT, "%06d", otp);
              } catch (Exception e) {
                            throw new IllegalStateException("Failed to compute TOTP code", e);
              }
    }

    // ---- Base32 (RFC 4648), implemented by hand: java.util.Base64 does not support Base32 ----

    private String base32Encode(byte[] data) {
              StringBuilder sb = new StringBuilder();
              int bits = 0;
              int value = 0;
              for (byte b : data) {
                            value = (value << 8) | (b & 0xFF);
                            bits += 8;
                            while (bits >= 5) {
                                              sb.append(BASE32_ALPHABET[(value >>> (bits - 5)) & 0x1F]);
                                              bits -= 5;
                            }
              }
              if (bits > 0) {
                            sb.append(BASE32_ALPHABET[(value << (5 - bits)) & 0x1F]);
              }
              return sb.toString();
    }

    private byte[] base32Decode(String encoded) {
              String clean = encoded.trim().toUpperCase(Locale.ROOT).replace("=", "");
              java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
              int bits = 0;
              int value = 0;
              for (char c : clean.toCharArray()) {
                            int idx = indexOf(c);
                            if (idx < 0) {
                                              continue; // skip separators/whitespace a user might paste in
                            }
                            value = (value << 5) | idx;
                            bits += 5;
                            if (bits >= 8) {
                                              out.write((value >>> (bits - 8)) & 0xFF);
                                              bits -= 8;
                            }
              }
              return out.toByteArray();
    }

    private int indexOf(char c) {
              for (int i = 0; i < BASE32_ALPHABET.length; i++) {
                            if (BASE32_ALPHABET[i] == c) {
                                              return i;
                            }
              }
              return -1;
    }

    private String urlEncode(String s) {
              return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
  }
