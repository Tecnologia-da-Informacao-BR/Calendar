package br.com.calendar.configuration.dto;

import br.com.calendar.configuration.DefaultView;
import br.com.calendar.configuration.Theme;
import br.com.calendar.configuration.TimeFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateConfigurationDTO(
        @Size(max = 10, message = "Language must not exceed 10 characters")
        String language,
        Theme theme,
        TimeFormat timeFormat,
        @Min(value = 1, message = "Week start day must be between 1 and 7")
        @Max(value = 7, message = "Week start day must be between 1 and 7")
        Short weekStartDay,
        DefaultView defaultView) {
}
