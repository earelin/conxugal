package gal.conxugal.application.rest.user;

import gal.conxugal.domain.user.FindCurrentUser;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;

@Controller("/api/me")
@Secured(SecurityRule.IS_AUTHENTICATED)
class CurrentUserController {

  private final FindCurrentUser findCurrentUser;

  CurrentUserController(FindCurrentUser findCurrentUser) {
    this.findCurrentUser = findCurrentUser;
  }

  @Get
  CurrentUserResponse me(Authentication authentication) {
    return CurrentUserResponse.of(findCurrentUser.find(authentication.getName()));
  }
}
