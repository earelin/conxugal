package gal.conxugal.application.rest.admin.status;

import gal.conxugal.domain.status.SystemStatusProbe;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;

@Controller("/api/admin/system-status")
@Secured("ADMIN")
class SystemStatusController {

  private final SystemStatusProbe systemStatusProbe;

  SystemStatusController(SystemStatusProbe systemStatusProbe) {
    this.systemStatusProbe = systemStatusProbe;
  }

  @Get
  SystemStatusResponse status() {
    return SystemStatusResponse.of(systemStatusProbe.currentStatus());
  }
}
