package br.com.calendar.auth;

import br.com.calendar.auth.dto.AuthResponse;
import br.com.calendar.auth.dto.ConfirmEmailRequest;
import br.com.calendar.auth.dto.ForgotPasswordRequest;
import br.com.calendar.auth.dto.LoginRequest;
import br.com.calendar.auth.dto.ResetPasswordRequest;
import br.com.calendar.auth.dto.SignupRequest;
import br.com.calendar.auth.dto.VerifyOtpRequest;
import br.com.calendar.auth.dto.VerifyOtpResponse;
import br.com.calendar.common.dto.MessageResponse;
import br.com.calendar.email.EmailService;
import br.com.calendar.user.User;
import br.com.calendar.user.UserRepository;
import br.com.calendar.user.UserService;
import br.com.calendar.user.dto.CreateUserDTO;
import br.com.calendar.user.dto.OtpResponseDTO;
import br.com.calendar.user.dto.UserResponseDTO;
import br.com.calendar.user.dto.UserSummaryDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String USER_ID = "usr_abc123";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private TokenBlacklist tokenBlacklist;

    @Mock
    private EmailService emailService;

    @Mock
    private RateLimiter rateLimiter;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, userService, passwordEncoder, jwtUtil, tokenBlacklist,
                emailService, rateLimiter, "http://localhost:4200");
    }

    @Test
    void signupCreatesUserAndReturnsToken() {
        UserResponseDTO created = new UserResponseDTO(
                USER_ID, "Danillo", "test@example.com", false, Instant.now(), Instant.now());

        when(userService.createUser(any(CreateUserDTO.class))).thenReturn(created);
        when(jwtUtil.generateEmailConfirmationToken(USER_ID)).thenReturn("confirmation-token");
        when(jwtUtil.generateToken(USER_ID)).thenReturn("jwt-token");
        when(jwtUtil.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.signup(
                new SignupRequest("Danillo", "test@example.com", "plain-password"));

        assertEquals("jwt-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        verify(emailService).send(eq("test@example.com"), any(String.class), contains("confirmation-token"));
    }

    @Test
    void loginWithValidCredentialsReturnsToken() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("test@example.com");
        user.setPassword("hashed-password");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-password", "hashed-password")).thenReturn(true);
        when(jwtUtil.generateToken(USER_ID)).thenReturn("jwt-token");
        when(jwtUtil.getExpirationMs()).thenReturn(86400000L);

        AuthResponse response = authService.login(new LoginRequest("test@example.com", "plain-password"));

        assertEquals("jwt-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        verify(rateLimiter, never()).recordAttempt(any(String.class));
    }

    @Test
    void loginWithTooManyAttemptsThrows() {
        when(rateLimiter.isBlocked("login:test@example.com")).thenReturn(true);

        assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequest("test@example.com", "plain-password")));

        verify(userRepository, never()).findByEmail(any(String.class));
    }

    @Test
    void requestPasswordResetWithValidEmailGeneratesOtp() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("test@example.com");
        user.setPassword("hashed-password");

        when(userRepository.findByEmail(any(String.class))).thenReturn(Optional.of(user));

        SecureRandom random = new SecureRandom();
        when(userService.generatePasswordResetOtp(any(String.class)))
                .thenReturn(
                        new OtpResponseDTO(String.format("%06d", random.nextInt(1_000_000)))
                );

        MessageResponse messageResponse = authService.requestPasswordReset(new ForgotPasswordRequest("test@example.com"));

        assertEquals("If the email is registered, password reset instructions will be sent.", messageResponse.message());
        verify(userService).generatePasswordResetOtp(user.getId());
        verify(emailService).send(eq("test@example.com"), any(String.class), any(String.class));
    }

    @Test
    void requestPasswordResetWithNonExistingEmailReturnsDefaultMessage() {
        when(userRepository.findByEmail(any(String.class))).thenReturn(Optional.empty());

        MessageResponse messageResponse = authService.requestPasswordReset(new ForgotPasswordRequest("test@example.com"));

        assertEquals("If the email is registered, password reset instructions will be sent.", messageResponse.message());
    }

    @Test
    void requestPasswordResetWithTooManyAttemptsThrows() {
        when(rateLimiter.isBlocked("forgot-password:test@example.com")).thenReturn(true);

        assertThrows(ResponseStatusException.class,
                () -> authService.requestPasswordReset(new ForgotPasswordRequest("test@example.com")));

        verify(userRepository, never()).findByEmail(any(String.class));
    }

    @Test
    void loginWithWrongPasswordThrows() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("test@example.com");
        user.setPassword("hashed-password");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequest("test@example.com", "wrong-password")));

        verify(rateLimiter).recordAttempt("login:test@example.com");
    }

    @Test
    void loginWithUnknownEmailThrows() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequest("unknown@example.com", "any-password")));

        verify(rateLimiter).recordAttempt("login:unknown@example.com");
    }

    @Test
    void verifyOtpWithValidCodeReturnsResetTokenAndBurnsTheOtp() {
        User user = userWithOtp("123456", LocalDateTime.now().plusMinutes(5));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generatePasswordResetToken(USER_ID)).thenReturn("reset-token");
        when(jwtUtil.getResetExpirationMs()).thenReturn(300000L);

        VerifyOtpResponse response = authService.verifyOtp(
                new VerifyOtpRequest("test@example.com", "123456"));

        assertEquals("reset-token", response.resetToken());
        assertEquals(300, response.expiresIn());
        assertNull(user.getOtp());
        assertNull(user.getOtpExpiration());
        verify(userRepository).save(user);
        verify(rateLimiter, never()).recordAttempt(any(String.class));
    }

    @Test
    void verifyOtpWithTooManyAttemptsThrows() {
        when(rateLimiter.isBlocked("verify-otp:test@example.com")).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> authService.verifyOtp(
                new VerifyOtpRequest("test@example.com", "123456")));

        verify(userRepository, never()).findByEmail(any(String.class));
    }

    @Test
    void verifyOtpWithWrongCodeThrows() {
        User user = userWithOtp("123456", LocalDateTime.now().plusMinutes(5));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        assertThrows(ResponseStatusException.class, () -> authService.verifyOtp(
                new VerifyOtpRequest("test@example.com", "999999")));
        verify(userRepository, never()).save(any(User.class));
        verify(rateLimiter).recordAttempt("verify-otp:test@example.com");
    }

    @Test
    void verifyOtpWithExpiredCodeThrows() {
        User user = userWithOtp("123456", LocalDateTime.now().minusMinutes(1));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        assertThrows(ResponseStatusException.class, () -> authService.verifyOtp(
                new VerifyOtpRequest("test@example.com", "123456")));
    }

    @Test
    void verifyOtpWithAlreadyUsedCodeThrows() {
        User user = userWithOtp(null, null);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        assertThrows(ResponseStatusException.class, () -> authService.verifyOtp(
                new VerifyOtpRequest("test@example.com", "123456")));
    }

    @Test
    void verifyOtpWithUnknownEmailThrowsTheSameErrorAsAWrongCode() {
        User user = userWithOtp("123456", LocalDateTime.now().plusMinutes(5));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        ResponseStatusException unknownEmail = assertThrows(ResponseStatusException.class,
                () -> authService.verifyOtp(new VerifyOtpRequest("unknown@example.com", "123456")));
        ResponseStatusException wrongCode = assertThrows(ResponseStatusException.class,
                () -> authService.verifyOtp(new VerifyOtpRequest("test@example.com", "999999")));

        // identical status and message, otherwise the route leaks which emails are registered
        assertEquals(wrongCode.getStatusCode(), unknownEmail.getStatusCode());
        assertEquals(wrongCode.getReason(), unknownEmail.getReason());
    }

    private static User userWithOtp(String otp, LocalDateTime expiration) {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("test@example.com");
        user.setOtp(otp);
        user.setOtpExpiration(expiration);
        return user;
    }

    @Test
    void meReturnsUserData() {
        User user = new User();
        user.setId(USER_ID);
        user.setName("Danillo");
        user.setEmail("test@example.com");

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        UserSummaryDTO response = authService.me(USER_ID);

        assertEquals(USER_ID, response.id());
        assertEquals("Danillo", response.name());
        assertEquals("test@example.com", response.email());
    }

    @Test
    void logoutRevokesToken() {
        authService.logout("jwt-token");

        verify(tokenBlacklist).revoke("jwt-token");
    }

    @Test
    void logoutWithBlankTokenThrows() {
        assertThrows(ResponseStatusException.class, () -> authService.logout(" "));
    }

    @Test
    void logoutWithMalformedTokenThrows() {
        when(jwtUtil.getExpirationDate("not-a-real-jwt"))
                .thenThrow(new io.jsonwebtoken.MalformedJwtException("Invalid JWT"));

        assertThrows(ResponseStatusException.class, () -> authService.logout("not-a-real-jwt"));
    }

    @Test
    void resetPasswordWithValidResetTokenSucceeds() {
        ResetPasswordRequest request = new ResetPasswordRequest("reset-token", "newPassword123", "newPassword123");

        when(tokenBlacklist.isRevoked("reset-token")).thenReturn(false);
        when(jwtUtil.isExpired("reset-token")).thenReturn(false);
        when(jwtUtil.getScope("reset-token")).thenReturn(JwtUtil.SCOPE_PASSWORD_RESET);
        when(jwtUtil.extractUserId("reset-token")).thenReturn(USER_ID);

        authService.resetPassword(request);

        verify(userService).resetPassword(USER_ID, "newPassword123");
        verify(tokenBlacklist).revoke("reset-token");
    }

    @Test
    void resetPasswordWithMismatchedPasswordsThrows() {
        ResetPasswordRequest request = new ResetPasswordRequest("reset-token", "newPassword123", "differentPassword");

        assertThrows(ResponseStatusException.class, () -> authService.resetPassword(request));

        verify(userService, never()).resetPassword(any(String.class), any(String.class));
    }

    @Test
    void resetPasswordWithNonResetScopeTokenThrows() {
        ResetPasswordRequest request = new ResetPasswordRequest("access-token", "newPassword123", "newPassword123");

        when(tokenBlacklist.isRevoked("access-token")).thenReturn(false);
        when(jwtUtil.isExpired("access-token")).thenReturn(false);
        when(jwtUtil.getScope("access-token")).thenReturn(null);

        assertThrows(ResponseStatusException.class, () -> authService.resetPassword(request));

        verify(userService, never()).resetPassword(any(String.class), any(String.class));
    }

    @Test
    void resetPasswordWithExpiredResetTokenThrows() {
        ResetPasswordRequest request = new ResetPasswordRequest("reset-token", "newPassword123", "newPassword123");

        when(tokenBlacklist.isRevoked("reset-token")).thenReturn(false);
        when(jwtUtil.isExpired("reset-token")).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> authService.resetPassword(request));

        verify(userService, never()).resetPassword(any(String.class), any(String.class));
    }

    @Test
    void resetPasswordWithAlreadyUsedResetTokenThrows() {
        ResetPasswordRequest request = new ResetPasswordRequest("reset-token", "newPassword123", "newPassword123");

        when(tokenBlacklist.isRevoked("reset-token")).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> authService.resetPassword(request));

        verify(userService, never()).resetPassword(any(String.class), any(String.class));
    }

    @Test
    void resetPasswordWithMalformedResetTokenThrows() {
        ResetPasswordRequest request = new ResetPasswordRequest("not-a-real-jwt", "newPassword123", "newPassword123");

        when(tokenBlacklist.isRevoked("not-a-real-jwt")).thenReturn(false);
        when(jwtUtil.isExpired("not-a-real-jwt"))
                .thenThrow(new io.jsonwebtoken.MalformedJwtException("Invalid JWT"));

        assertThrows(ResponseStatusException.class, () -> authService.resetPassword(request));

        verify(userService, never()).resetPassword(any(String.class), any(String.class));
    }

    @Test
    void confirmEmailWithValidTokenMarksEmailConfirmed() {
        ConfirmEmailRequest request = new ConfirmEmailRequest("confirmation-token");

        when(tokenBlacklist.isRevoked("confirmation-token")).thenReturn(false);
        when(jwtUtil.isExpired("confirmation-token")).thenReturn(false);
        when(jwtUtil.getScope("confirmation-token")).thenReturn(JwtUtil.SCOPE_EMAIL_CONFIRMATION);
        when(jwtUtil.extractUserId("confirmation-token")).thenReturn(USER_ID);

        authService.confirmEmail(request);

        verify(userService).markEmailConfirmed(USER_ID);
        verify(tokenBlacklist, never()).revoke(any(String.class));
    }

    @Test
    void confirmEmailWithPasswordResetScopeTokenThrows() {
        ConfirmEmailRequest request = new ConfirmEmailRequest("reset-token");

        when(tokenBlacklist.isRevoked("reset-token")).thenReturn(false);
        when(jwtUtil.isExpired("reset-token")).thenReturn(false);
        when(jwtUtil.getScope("reset-token")).thenReturn(JwtUtil.SCOPE_PASSWORD_RESET);

        assertThrows(ResponseStatusException.class, () -> authService.confirmEmail(request));

        verify(userService, never()).markEmailConfirmed(any(String.class));
    }

    @Test
    void confirmEmailWithExpiredTokenThrows() {
        ConfirmEmailRequest request = new ConfirmEmailRequest("confirmation-token");

        when(tokenBlacklist.isRevoked("confirmation-token")).thenReturn(false);
        when(jwtUtil.isExpired("confirmation-token")).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> authService.confirmEmail(request));

        verify(userService, never()).markEmailConfirmed(any(String.class));
    }
}