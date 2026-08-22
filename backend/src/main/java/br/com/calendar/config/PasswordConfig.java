package br.com.calendar.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    @Value("${secret.password.pepper}")
    private String pepper;

    @Bean
    public PasswordEncoder passwordEncoder(){
        PasswordEncoder bcryptPasswordEncoder = new BCryptPasswordEncoder();

        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                if(rawPassword == null){
                    throw new IllegalArgumentException("rawPassword cannot be null");
                }
                return bcryptPasswordEncoder.encode(rawPassword + pepper);
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                if(rawPassword ==null || encodedPassword == null){
                    return false;
                }
                return bcryptPasswordEncoder.matches(rawPassword + pepper, encodedPassword);
            }
        };
    }
}
