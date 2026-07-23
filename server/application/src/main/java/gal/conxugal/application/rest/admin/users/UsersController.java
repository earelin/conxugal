package gal.conxugal.application.rest.admin.users;

import gal.conxugal.domain.user.CreateUser;
import gal.conxugal.domain.user.DuplicateEmailException;
import gal.conxugal.domain.user.LastEnabledAdminException;
import gal.conxugal.domain.user.ListUsers;
import gal.conxugal.domain.user.SetUserEnabled;
import gal.conxugal.domain.user.UserNotFoundException;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import io.micronaut.security.annotation.Secured;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@Controller("/api/admin/users")
@Secured("ADMIN")
class UsersController {

  private final ListUsers listUsers;
  private final CreateUser createUser;
  private final SetUserEnabled setUserEnabled;

  UsersController(ListUsers listUsers, CreateUser createUser, SetUserEnabled setUserEnabled) {
    this.listUsers = listUsers;
    this.createUser = createUser;
    this.setUserEnabled = setUserEnabled;
  }

  @Get
  List<UserResponse> list() {
    return listUsers.list().stream().map(UserResponse::of).toList();
  }

  @Post
  @Status(HttpStatus.CREATED)
  @SuppressWarnings("PMD.PreserveStackTrace")
  CreatedUserResponse create(@Valid @Body CreateUserRequest request) {
    // Business-outcome translation, not failure handling: the domain exception carries no
    // stack trace worth preserving beyond what the Problem's own message already states.
    try {
      return CreatedUserResponse.of(createUser.create(request.email(), request.role()));
    } catch (DuplicateEmailException e) {
      throw new DuplicateEmailProblem(request.email());
    }
  }

  @Post("/{id}/enabled")
  @SuppressWarnings("PMD.PreserveStackTrace")
  UserResponse setEnabled(@PathVariable UUID id, @Valid @Body SetEnabledRequest request) {
    try {
      return UserResponse.of(setUserEnabled.setEnabled(id, request.enabled()));
    } catch (UserNotFoundException e) {
      throw new UserNotFoundProblem(id);
    } catch (LastEnabledAdminException e) {
      throw new LastEnabledAdminProblem(id);
    }
  }
}
