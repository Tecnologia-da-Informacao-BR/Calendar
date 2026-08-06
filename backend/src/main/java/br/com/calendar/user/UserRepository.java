package br.com.calendar.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<String, User> {

    Optional<User> findUserByEmailIgnoreCase(String email);

}
