package com.sanitaslink.core.crypto;

import com.sanitaslink.core.config.CryptoProperties;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * AES-GCM encryption-at-rest for sensitive string fields. Each value uses a fresh 96-bit random IV
 * prepended to the ciphertext, and the payload is stored as {@code v<version>:<base64>}. The
 * version prefix supports key rotation: new values are written with the current key, while values
 * written with the previous key remain readable via {@code previousKey} until re-encrypted.
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int GCM_TAG_BITS = 128;
  private static final int IV_LENGTH = 12;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final int version;
  private final SecretKey key;
  private final SecretKey previousKey;

  public EncryptedStringConverter(CryptoProperties properties) {
    String encodedKey = properties.getKey();
    if (encodedKey == null || encodedKey.isBlank()) {
      throw new IllegalStateException(
          "sanitaslink.security.encryption.key must be configured (set SANITASLINK_ENCRYPTION_KEY)");
    }
    this.version = properties.getVersion();
    this.key = new SecretKeySpec(requireKeySize(encodedKey), "AES");
    String previous = properties.getPreviousKey();
    this.previousKey =
        previous == null || previous.isBlank()
            ? null
            : new SecretKeySpec(requireKeySize(previous), "AES");
  }

  @Override
  public String convertToDatabaseColumn(String attribute) {
    if (attribute == null) {
      return null;
    }
    try {
      byte[] iv = new byte[IV_LENGTH];
      RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] ciphertext = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
      byte[] combined = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
      return "v" + version + ":" + Base64.getEncoder().encodeToString(combined);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to encrypt sensitive field", e);
    }
  }

  @Override
  public String convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    try {
      Stored stored = parse(dbData);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE, stored.secretKey(), new GCMParameterSpec(GCM_TAG_BITS, stored.iv()));
      return new String(cipher.doFinal(stored.ciphertext()), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to decrypt sensitive field", e);
    }
  }

  private Stored parse(String value) {
    int valueVersion;
    String payload;
    int colon = value.indexOf(':');
    if (colon > 0 && value.startsWith("v") && isDigits(value.substring(1, colon))) {
      valueVersion = Integer.parseInt(value.substring(1, colon));
      payload = value.substring(colon + 1);
    } else {
      // Legacy values written before versioning: treat as the current version.
      valueVersion = version;
      payload = value;
    }

    SecretKey decryptKey;
    if (valueVersion == version) {
      decryptKey = key;
    } else if (previousKey != null && valueVersion == version - 1) {
      decryptKey = previousKey;
    } else {
      throw new IllegalStateException("No key available for encryption version " + valueVersion);
    }

    byte[] combined = Base64.getDecoder().decode(payload);
    byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
    byte[] ciphertext = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
    return new Stored(decryptKey, iv, ciphertext);
  }

  private boolean isDigits(String value) {
    if (value.isEmpty()) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      if (!Character.isDigit(value.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private byte[] requireKeySize(String base64Key) {
    byte[] bytes = Base64.getDecoder().decode(base64Key);
    if (bytes.length != 32) {
      throw new IllegalStateException("sanitaslink.security.encryption key must be 32 bytes");
    }
    return bytes;
  }

  private record Stored(SecretKey secretKey, byte[] iv, byte[] ciphertext) {}
}
