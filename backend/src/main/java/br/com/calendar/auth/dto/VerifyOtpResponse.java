package br.com.calendar.auth.dto;


public record VerifyOtpResponse(String resetToken, long expiresIn) {
}
