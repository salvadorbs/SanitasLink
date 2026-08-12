package com.sanitaslink.core.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sanitaslink.core.config.CryptoProperties;
import org.junit.jupiter.api.Test;

class EncryptedStringConverterTest {

  private static final String KEY1 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
  private static final String KEY2 = "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=";

  private CryptoProperties properties(int version, String key, String previousKey) {
    CryptoProperties props = new CryptoProperties();
    props.setVersion(version);
    props.setKey(key);
    props.setPreviousKey(previousKey);
    return props;
  }

  @Test
  void roundTripsAndUsesVersionPrefix() {
    EncryptedStringConverter converter = new EncryptedStringConverter(properties(1, KEY1, null));
    String stored = converter.convertToDatabaseColumn("sensitive value");
    assertThat(stored).startsWith("v1:");
    assertThat(stored).isNotEqualTo("sensitive value");
    assertThat(converter.convertToEntityAttribute(stored)).isEqualTo("sensitive value");
  }

  @Test
  void handlesNull() {
    EncryptedStringConverter converter = new EncryptedStringConverter(properties(1, KEY1, null));
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }

  @Test
  void wrongKeyFailsSafely() {
    EncryptedStringConverter writer = new EncryptedStringConverter(properties(1, KEY1, null));
    String stored = writer.convertToDatabaseColumn("secret");
    EncryptedStringConverter reader = new EncryptedStringConverter(properties(2, KEY2, null));
    assertThatThrownBy(() -> reader.convertToEntityAttribute(stored))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void previousKeyReadsOldDataDuringRotation() {
    EncryptedStringConverter oldWriter = new EncryptedStringConverter(properties(1, KEY1, null));
    String stored = oldWriter.convertToDatabaseColumn("old data");

    // New key + previous key configured: old ciphertext remains readable.
    EncryptedStringConverter rotatingReader =
        new EncryptedStringConverter(properties(2, KEY2, KEY1));
    assertThat(rotatingReader.convertToEntityAttribute(stored)).isEqualTo("old data");

    // New writes use the new key version.
    String newStored = rotatingReader.convertToDatabaseColumn("new data");
    assertThat(newStored).startsWith("v2:");
    assertThat(newStored).isNotEqualTo(stored);
  }
}
