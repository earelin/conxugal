package gal.conxugal.domain.user;

import jakarta.inject.Singleton;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates a random initial password meeting the fixed strength policy: at least 16
 * characters, mixing uppercase letters, lowercase letters, digits and symbols.
 */
@Singleton
public class PasswordGenerator {

  private static final int LENGTH = 16;
  private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
  private static final String DIGITS = "0123456789";
  private static final String SYMBOLS = "!@#$%^&*()-_=+[]{}";
  private static final List<String> CHARACTER_CLASSES =
      List.of(UPPERCASE, LOWERCASE, DIGITS, SYMBOLS);
  private static final String ALL_CHARACTERS = UPPERCASE + LOWERCASE + DIGITS + SYMBOLS;

  private final SecureRandom secureRandom = new SecureRandom();

  public GeneratedPassword generate() {
    List<Character> characters = new ArrayList<>(LENGTH);
    for (String characterClass : CHARACTER_CLASSES) {
      characters.add(randomCharacterFrom(characterClass));
    }
    while (characters.size() < LENGTH) {
      characters.add(randomCharacterFrom(ALL_CHARACTERS));
    }
    Collections.shuffle(characters, secureRandom);

    StringBuilder password = new StringBuilder(LENGTH);
    characters.forEach(password::append);
    return new GeneratedPassword(password.toString());
  }

  private char randomCharacterFrom(String characterClass) {
    return characterClass.charAt(secureRandom.nextInt(characterClass.length()));
  }
}
