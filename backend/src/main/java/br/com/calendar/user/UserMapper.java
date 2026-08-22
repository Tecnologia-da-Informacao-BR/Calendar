package br.com.calendar.user;

import br.com.calendar.user.dto.CreateUserDTO;
import br.com.calendar.user.dto.UserResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponseDTO toResponse(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.isEmailConfirmed(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    public User toEntity(CreateUserDTO dto) {
        User user = new User();
        user.setName(dto.name());
        user.setEmail(dto.email());
        user.setPassword(dto.password()); // not yet encoded — the service does that
        return user;
    }
}