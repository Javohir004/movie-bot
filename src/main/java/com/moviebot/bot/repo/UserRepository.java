package com.moviebot.bot.repo;

import com.moviebot.bot.enums.Role;
import com.moviebot.bot.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserId(Long userId);

    boolean existsByUserIdAndRole(Long userId, Role role);

    List<User> findByRole(Role role);
}
