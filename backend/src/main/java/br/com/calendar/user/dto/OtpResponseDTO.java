package br.com.calendar.user.dto;

// Internal transfer object between UserService and AuthService, which emails
// the code — never returned directly by an HTTP endpoint.
public record OtpResponseDTO(String otp) {
}
