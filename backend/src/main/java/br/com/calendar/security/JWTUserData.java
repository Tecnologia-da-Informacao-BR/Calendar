package br.com.calendar.security;

import lombok.Builder;

@Builder
public record JWTUserData(String userId, String name, String email) {
}
