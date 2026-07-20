package gal.conxugal.domain.auth;

import jakarta.inject.Singleton;
import java.util.List;

/** Lists every account, enabled and disabled alike. */
@Singleton
public class ListUsers {

  private final UserRepository userRepository;

  public ListUsers(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public List<User> list() {
    return userRepository.findAll();
  }
}
