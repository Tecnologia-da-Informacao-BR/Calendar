package br.com.calendar.configuration;

import br.com.calendar.configuration.dto.ConfigurationResponseDTO;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/configuration")
public class ConfigurationController {

    private final ConfigurationService configurationService;

    @GetMapping
    public ResponseEntity<ConfigurationResponseDTO> getConfiguration(Authentication authentication) {
        return ResponseEntity.ok(configurationService.getDefaultConfiguration(authentication.getName()));
    }
}
