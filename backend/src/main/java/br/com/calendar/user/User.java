package br.com.calendar.user;

import br.com.calendar.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends BaseEntity implements UserDetails {
    @Column(name = "username")
    private String name;

    @Column(name = "avatar")
    private String avatar;

    @Column(name = "email")
    private String email;

    @Column(name = "email_confirmed")
    private String email_confirmed;

    @Column(name = "password")
    private String password;

    @Column(name = "opt")
    private String opt;

    @Column(name = "opt_expiration")
    private String opt_expiration;

    public User(){}

    public User(String name, String avatar, String email, String email_confirmed, String password, String opt, String opt_expiration) {
        this.name = name;
        this.avatar = avatar;
        this.email = email;
        this.email_confirmed = email_confirmed;
        this.password = password;
        this.opt = opt;
        this.opt_expiration = opt_expiration;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }


}
