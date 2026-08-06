package br.com.calendar.configuration;

import br.com.calendar.common.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Entity
@Table(name = "configuration")
@AttributeOverride(name = "id", column = @Column(name = "user_id"))
public class Configuration extends BaseEntity {

    @Column(length = 20)
    private String theme;

    @Column(name = "time_format", length = 10)
    private String timeFormat;

    @Column(name = "week_start_day")
    private String weekStartDay;

    @Column(name = "default_view", length = 20)
    private String defaultView;
}