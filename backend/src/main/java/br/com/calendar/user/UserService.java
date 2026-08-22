package br.com.calendar.user;

import br.com.calendar.configuration.ConfigurationService;
import br.com.calendar.user.dto.ChangePasswordDTO;
import br.com.calendar.user.dto.CreateUserDTO;
import br.com.calendar.user.dto.OtpResponseDTO;
import br.com.calendar.user.dto.UpdateUserDTO;
import br.com.calendar.user.dto.UserResponseDTO;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class UserService {

    private static final long OTP_VALIDITY_MINUTES = 15;

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ConfigurationService configurationService;

    public UserService(UserRepository userRepository, UserMapper userMapper,
                        PasswordEncoder passwordEncoder, ConfigurationService configurationService) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.configurationService = configurationService;
    }

    public UserResponseDTO createUser(CreateUserDTO dto) {
        if (userRepository.findByEmail(dto.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to complete registration");
        }

        User user = userMapper.toEntity(dto);
        user.setEmailConfirmed(false);
        user.setPassword(passwordEncoder.encode(dto.password()));

        // The check above is best-effort: two concurrent signups for the
        // same email could both pass it before either INSERT commits. The
        // unique constraint on users.email (V3) is what actually prevents
        // the duplicate; this just turns that race into the same clean
        // error instead of a raw constraint-violation failure.
        User savedUser = saveWithUniqueEmail(user, "Unable to complete registration");
        configurationService.createDefaultConfiguration(savedUser.getId());

        return userMapper.toResponse(savedUser);
    }

    public UserResponseDTO getUserByID(String id) {
        User user = findUserOrThrow(id);
        return userMapper.toResponse(user);
    }

    public UserResponseDTO getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with email: " + email));

        return userMapper.toResponse(user);
    }

    public UserResponseDTO updateUser(String id, UpdateUserDTO dto) {
        User user = findUserOrThrow(id);

        if (dto.name() != null) {
            user.setName(dto.name());
        }
        if (dto.email() != null) {
            user.setEmail(dto.email());
        }

        User savedUser = saveWithUniqueEmail(user, "Email is already in use");

        return userMapper.toResponse(savedUser);
    }

    public OtpResponseDTO generatePasswordResetOtp(String id) {
        User user = findUserOrThrow(id);

        String otp = generateOtp();
        user.setOtp(otp);
        user.setOtpExpiration(LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES));
        userRepository.save(user);

        // The caller (AuthService) is responsible for emailing this code.
        return new OtpResponseDTO(otp);
    }

    // The caller (AuthService) is responsible for identifying and authorizing
    // which user this is via the email-confirmation JWT before calling this.
    public void markEmailConfirmed(String id) {
        User user = findUserOrThrow(id);
        user.setEmailConfirmed(true);
        userRepository.save(user);
    }

    public void changePassword(String id, ChangePasswordDTO dto) {
        User user = findUserOrThrow(id);

        if (!passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);
    }

    // The caller (AuthService) is responsible for identifying and authorizing
    // which user this is via the password-reset JWT before calling this.
    public void resetPassword(String id, String newPassword) {
        User user = findUserOrThrow(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private User saveWithUniqueEmail(User user, String duplicateEmailMessage) {
        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, duplicateEmailMessage, e);
        }
    }

    private User findUserOrThrow(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id));
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        int otp = random.nextInt(1_000_000);
        return String.format("%06d", otp);
    }
}