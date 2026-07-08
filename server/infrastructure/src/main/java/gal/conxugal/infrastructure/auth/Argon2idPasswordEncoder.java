package gal.conxugal.infrastructure.auth;

import gal.conxugal.domain.auth.PasswordEncoder;
import jakarta.inject.Singleton;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/**
 * Salted-hash {@link PasswordEncoder} adapter: Argon2id with parameters following
 * OWASP's current guidance for server-side interactive login. The encoded form is
 * {@code memory:iterations:parallelism:salt:hash} (both Base64), so future parameter
 * tuning won't invalidate hashes already stored.
 */
@Singleton
public class Argon2idPasswordEncoder implements PasswordEncoder {

  private static final int MEMORY_KB = 65536;
  private static final int ITERATIONS = 3;
  private static final int PARALLELISM = 1;
  private static final int HASH_LENGTH_BYTES = 32;
  private static final int SALT_LENGTH_BYTES = 16;

  private final SecureRandom secureRandom = new SecureRandom();
  private final String dummyHash = encode("");

  @Override
  public String encode(String rawPassword) {
    byte[] salt = new byte[SALT_LENGTH_BYTES];
    secureRandom.nextBytes(salt);
    byte[] hash = hash(rawPassword, salt, MEMORY_KB, ITERATIONS, PARALLELISM);
    return MEMORY_KB + ":" + ITERATIONS + ":" + PARALLELISM + ":" + base64(salt)
        + ":" + base64(hash);
  }

  @Override
  public boolean matches(String rawPassword, String encodedPassword) {
    String[] parts = encodedPassword.split(":", 5);
    if (parts.length != 5) {
      throw new IllegalArgumentException("Malformed encoded password");
    }
    int memoryKb = Integer.parseInt(parts[0]);
    int iterations = Integer.parseInt(parts[1]);
    int parallelism = Integer.parseInt(parts[2]);
    byte[] salt = Base64.getDecoder().decode(parts[3]);
    byte[] expectedHash = Base64.getDecoder().decode(parts[4]);

    byte[] actualHash = hash(rawPassword, salt, memoryKb, iterations, parallelism);
    return MessageDigest.isEqual(expectedHash, actualHash);
  }

  @Override
  public void matchAgainstDummyHash(String rawPassword) {
    matches(rawPassword, dummyHash);
  }

  private byte[] hash(
      String rawPassword, byte[] salt, int memoryKb, int iterations, int parallelism) {
    Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
        .withMemoryAsKB(memoryKb)
        .withIterations(iterations)
        .withParallelism(parallelism)
        .withSalt(salt)
        .build();

    Argon2BytesGenerator generator = new Argon2BytesGenerator();
    generator.init(parameters);

    byte[] hash = new byte[HASH_LENGTH_BYTES];
    generator.generateBytes(rawPassword.toCharArray(), hash);
    return hash;
  }

  private static String base64(byte[] bytes) {
    return Base64.getEncoder().encodeToString(bytes);
  }
}
