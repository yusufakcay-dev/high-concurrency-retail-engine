package io.github.yusufakcay_dev.user_service.repository;

import io.github.yusufakcay_dev.user_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
}