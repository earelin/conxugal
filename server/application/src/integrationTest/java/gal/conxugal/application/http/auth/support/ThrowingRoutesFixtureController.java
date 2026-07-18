package gal.conxugal.application.http.auth.support;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

@Controller
public class ThrowingRoutesFixtureController {

  @Secured(SecurityRule.IS_ANONYMOUS)
  @Get("/test-support/boom")
  String boom() {
    throw new BoomException();
  }

  @Secured(SecurityRule.IS_AUTHENTICATED)
  @Get("/api/test-support/boom")
  String apiBoom() {
    throw new BoomException();
  }

  private static final class BoomException extends RuntimeException {

    BoomException() {
      super("boom - sensitive detail that must never reach a response body");
    }
  }
}
