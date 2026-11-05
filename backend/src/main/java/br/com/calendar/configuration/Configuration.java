package br.com.calendar.configuration;

import br.com.calendar.common.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

@Getter
@Setter
@ToString
@Entity
@Table(name = "configuration")
@AttributeOverride(name = "id", column = @Column(name = "user_id"))
public class Configuration extends BaseEntity {

    @Column(length = 10)
    private String language;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(columnDefinition = "theme_type")
    private Theme theme;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "time_format", columnDefinition = "time_format_type")
    private TimeFormat timeFormat;

    @Column(name = "week_start_day")
    private Short weekStartDay;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "default_view", columnDefinition = "view_type")
    private DefaultView defaultView;

    @Column(name = "in_app_notifications_enabled", nullable = false)
    private boolean inAppNotificationsEnabled = true;

    @Column(name = "email_notifications_enabled", nullable = false)
    private boolean emailNotificationsEnabled = false;

    @Column(name = "browser_push_notifications_enabled", nullable = false)
    private boolean browserPushNotificationsEnabled = false;

}