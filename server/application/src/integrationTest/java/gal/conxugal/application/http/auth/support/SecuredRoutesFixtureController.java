package gal.conxugal.application.http.auth.support;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

@Controller("/api")
public class SecuredRoutesFixtureController {

    @Secured(SecurityRule.IS_AUTHENTICATED)
    @Get("/data")
    HttpResponse<?> data() {
        return HttpResponse.ok();
    }

    @Secured(SecurityRule.IS_AUTHENTICATED)
    @Post("/data")
    HttpResponse<?> createData() {
        return HttpResponse.ok();
    }

    @Secured("ADMIN")
    @Get("/admin/reports")
    HttpResponse<?> adminReports() {
        return HttpResponse.ok();
    }
}
