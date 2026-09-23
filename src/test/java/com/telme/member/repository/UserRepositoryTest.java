package com.telme.member.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.member.entity.User;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void 이메일이_같으면_저장_시_UNIQUE_제약을_위반한다() {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        userRepository.save(User.builder().email(email).passwordHash("HASHED").build());
        userRepository.flush();

        assertThatThrownBy(() -> {
            userRepository.save(User.builder().email(email).passwordHash("HASHED2").build());
            userRepository.flush();
        })
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(Throwable::getCause)
                .isInstanceOf(ConstraintViolationException.class)
                .extracting(cause -> ((ConstraintViolationException) cause).getConstraintName())
                .isEqualTo("users_email_key");
    }

    @Test
    void 이메일이_다르면_정상_저장된다() {
        String email = "unique-" + UUID.randomUUID() + "@example.com";

        User saved = userRepository.save(User.builder().email(email).passwordHash("HASHED").build());
        userRepository.flush();

        assertThat(userRepository.findByEmail(email)).contains(saved);
    }
}
