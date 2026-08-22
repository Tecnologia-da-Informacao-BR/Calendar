package br.com.calendar.user;

import br.com.calendar.configuration.ConfigurationService;
import br.com.calendar.user.dto.ChangePasswordDTO;
import br.com.calendar.user.dto.CreateUserDTO;
import br.com.calendar.user.dto.OtpResponseDTO;
import br.com.calendar.user.dto.UpdateUserDTO;
import br.com.calendar.user.dto.UserResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String USER_ID = "usr_abc123";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ConfigurationService configurationService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, userMapper, passwordEncoder, configurationService);
    }

    @Test
    void createUserWithNewEmailSucceeds() {
        CreateUserDTO dto = new CreateUserDTO("Danillo", "test@example.com", "plain-password");
        User mappedUser = new User();
        User savedUser = new User();
        savedUser.setId(USER_ID);
        UserResponseDTO expected = new UserResponseDTO(USER_ID, "Danillo", "test@example.com", false, null, null);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(userMapper.toEntity(dto)).thenReturn(mappedUser);
        when(passwordEncoder.encode("plain-password")).thenReturn("hashed-password");
        when(userRepository.save(mappedUser)).thenReturn(savedUser);
        when(userMapper.toResponse(savedUser)).thenReturn(expected);

        UserResponseDTO response = userService.createUser(dto);

        assertEquals(expected, response);
        assertEquals("hashed-password", mappedUser.getPassword());
        assertFalse(mappedUser.isEmailConfirmed());
        verify(configurationService).createDefaultConfiguration(USER_ID);
    }

    @Test
    void createUserWithExistingEmailThrows() {
        CreateUserDTO dto = new CreateUserDTO("Danillo", "test@example.com", "plain-password");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(new User()));

        assertThrows(ResponseStatusException.class, () -> userService.createUser(dto));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createUserWithEmailTakenByAConcurrentSignupThrows() {
        // findByEmail sees nothing (no race yet), but the unique constraint
        // catches it at insert time — the classic TOCTOU race.
        CreateUserDTO dto = new CreateUserDTO("Danillo", "test@example.com", "plain-password");
        User mappedUser = new User();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(userMapper.toEntity(dto)).thenReturn(mappedUser);
        when(passwordEncoder.encode("plain-password")).thenReturn("hashed-password");
        when(userRepository.save(mappedUser)).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(ResponseStatusException.class, () -> userService.createUser(dto));

        verify(configurationService, never()).createDefaultConfiguration(any(String.class));
    }

    @Test
    void updateUserChangesNameAndEmail() {
        User user = new User();
        user.setId(USER_ID);
        user.setName("Old Name");
        user.setEmail("old@example.com");
        UserResponseDTO expected = new UserResponseDTO(USER_ID, "New Name", "new@example.com", false, null, null);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(expected);

        UserResponseDTO response = userService.updateUser(USER_ID, new UpdateUserDTO("New Name", "new@example.com"));

        assertEquals(expected, response);
        assertEquals("New Name", user.getName());
        assertEquals("new@example.com", user.getEmail());
    }

    @Test
    void updateUserWithEmailAlreadyTakenByAnotherUserThrows() {
        User user = new User();
        user.setId(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(ResponseStatusException.class,
                () -> userService.updateUser(USER_ID, new UpdateUserDTO(null, "taken@example.com")));
    }

    @Test
    void generatePasswordResetOtpSetsThePasswordResetFields() {
        User user = new User();
        user.setId(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        OtpResponseDTO response = userService.generatePasswordResetOtp(USER_ID);

        assertEquals(user.getOtp(), response.otp());
        assertTrue(user.getOtpExpiration().isAfter(LocalDateTime.now()));
    }

    @Test
    void markEmailConfirmedSetsTheFlagAndSaves() {
        User user = new User();
        user.setId(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.markEmailConfirmed(USER_ID);

        assertTrue(user.isEmailConfirmed());
        verify(userRepository).save(user);
    }

    @Test
    void resetPasswordEncodesAndSavesTheNewPassword() {
        User user = new User();
        user.setId(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword123")).thenReturn("hashed-new-password");

        userService.resetPassword(USER_ID, "newPassword123");

        assertEquals("hashed-new-password", user.getPassword());
        verify(userRepository).save(user);
    }

    @Test
    void changePasswordWithCorrectCurrentPasswordSucceeds() {
        User user = new User();
        user.setId(USER_ID);
        user.setPassword("hashed-old-password");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old-password", "hashed-old-password")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("hashed-new-password");

        userService.changePassword(USER_ID, new ChangePasswordDTO("old-password", "new-password"));

        assertEquals("hashed-new-password", user.getPassword());
    }

    @Test
    void changePasswordWithWrongCurrentPasswordThrows() {
        User user = new User();
        user.setId(USER_ID);
        user.setPassword("hashed-old-password");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-old-password")).thenReturn(false);

        assertThrows(ResponseStatusException.class,
                () -> userService.changePassword(USER_ID, new ChangePasswordDTO("wrong-password", "new-password")));
    }
}
