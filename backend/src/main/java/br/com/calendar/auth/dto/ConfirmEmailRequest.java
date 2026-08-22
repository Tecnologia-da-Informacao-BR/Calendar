package br.com.calendar.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmEmailRequest(
        @NotBlank(message = "Confirmation token is required")
        String token
) {
}
