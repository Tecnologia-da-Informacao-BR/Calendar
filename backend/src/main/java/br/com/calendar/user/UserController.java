package br.com.calendar.user;

import br.com.calendar.user.dto.ChangePasswordDTO;
import br.com.calendar.user.dto.UpdateUserDTO;
import br.com.calendar.user.dto.UserResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get user by ID")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> getUserByID(@PathVariable String id) {
        checkOwnership(id);
        return ResponseEntity.ok(userService.getUserByID(id));
    }

    @Operation(summary = "Update user")
    @PatchMapping("/{id}")
    public ResponseEntity<UserResponseDTO> updateUser(@PathVariable String id, @Valid @RequestBody UpdateUserDTO dto) {
        checkOwnership(id);
        return ResponseEntity.ok(userService.updateUser(id, dto));
    }

    @Operation(summary = "Change user password")
    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> changePassword(@PathVariable String id, @Valid @RequestBody ChangePasswordDTO dto) {
        checkOwnership(id);
        userService.changePassword(id, dto);
        return ResponseEntity.noContent().build();
    }

    private void checkOwnership(String idFromUrl) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        String loggedInUserId = authentication.getName();
        if (!idFromUrl.equals(loggedInUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to access this resource");
        }
    }
}