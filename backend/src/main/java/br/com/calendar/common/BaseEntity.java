package br.com.calendar.common;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    private String id = NanoIdUtils.randomNanoId();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private LocalDate deletedAt = null;

    @Column(name = "active_status")
    private boolean active = true;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();

        if(!this.active){
            this.deletedAt = LocalDate.now();
        }
    }

}
