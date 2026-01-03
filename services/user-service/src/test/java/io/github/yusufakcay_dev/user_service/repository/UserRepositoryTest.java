package io.github.yusufakcay_dev.user_service.repository;

import io.github.yusufakcay_dev.user_service.config.PostgresTestContainerConfig;
import io.github.yusufakcay_dev.user_service.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository tests using PostgreSQL Testcontainers.
 * Focuses on custom queries and PostgreSQL-specific behavior.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepositoryTest extends PostgresTestContainerConfig {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Should find user by username")
    void shouldFindUserByUsername() {
        User user = User.builder()
                .username("testuser")
                .password("hashedpassword")
                .role(User.Role.CUSTOMER)
                .build();
        userRepository.save(user);

        var found = userRepository.findByUsername("testuser");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser");
        assertThat(found.get().getId()).isNotNull();
    }

    @Test
    @DisplayName("Should return empty for non-existent username")
    void shouldReturnEmptyForNonExistentUsername() {
        var found = userRepository.findByUsername("nonexistent");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Username lookup should be case-sensitive")
    void shouldBeCaseSensitive() {
        userRepository.save(User.builder()
                .username("TestUser")
                .password("password")
                .role(User.Role.CUSTOMER)
                .build());

        assertThat(userRepository.findByUsername("testuser")).isEmpty();
        assertThat(userRepository.findByUsername("TestUser")).isPresent();
    }

    @Test
    @DisplayName("Should enforce unique username constraint")
    void shouldEnforceUniqueUsername() {
        userRepository.save(User.builder()
                .username("duplicate")
                .password("pass1")
                .role(User.Role.CUSTOMER)
                .build());

        assertThatThrownBy(() -> {
            userRepository.save(User.builder()
                    .username("duplicate")
                    .password("pass2")
                    .role(User.Role.ADMIN)
                    .build());
            userRepository.flush();
        }).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should persist user with all fields")
    void shouldPersistUserWithAllFields() {
        User user = User.builder()
                .username("fulluser")
                .password("hashedpass")
                .role(User.Role.ADMIN)
                .build();

        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUsername()).isEqualTo("fulluser");
        assertThat(saved.getPassword()).isEqualTo("hashedpass");
        assertThat(saved.getRole()).isEqualTo(User.Role.ADMIN);
    }
}
