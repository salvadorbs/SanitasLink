package com.sanitaslink.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenGeneratorTest {

  @Test
  void randomTokenIsHighEntropyHex() {
    String first = TokenGenerator.randomToken();
    String second = TokenGenerator.randomToken();

    assertThat(first).hasSize(64).matches("[0-9a-f]{64}");
    assertThat(second).hasSize(64).isNotEqualTo(first);
  }

  @Test
  void sha256IsStableAndNonInvertible() {
    String value = "raw-token-value";
    String hash = TokenGenerator.sha256Hex(value);

    assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    assertThat(TokenGenerator.sha256Hex(value)).isEqualTo(hash);
    assertThat(hash).isNotEqualTo(value);
  }
}
