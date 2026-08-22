package br.com.calendar.domain;

import br.com.calendar.common.BaseEntity;
import br.com.calendar.category.Category;
import br.com.calendar.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.SQLDelete;

import java.time.Instant;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Table(name = "task")
@SQLDelete(sql = "UPDATE task SET deleted_at = now() WHERE id = ?")
public class Task extends BaseEntity {

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(length = 255)
    private String title;

    private String description;

    private String timezone;

    private String location;

    private String status;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    private Integer repeat;

    @Column(name = "repeat_interval", length = 15)
    private String repeatInterval;

    @Column(name = "all_day")
    private Boolean allDay;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}