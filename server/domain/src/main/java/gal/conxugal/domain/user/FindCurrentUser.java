package gal.conxugal.domain.user;

import jakarta.inject.Singleton;

/** Resolves the authenticated caller's own account by email. */
@Singleton
public class FindCurrentUser {

  private final UserRepository userRepository;

  public FindCurrentUser(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public User find(String email) {
    return userRepository.findByEmail(email).orElseThrow();
  }
}
