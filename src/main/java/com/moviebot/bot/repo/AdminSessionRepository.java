package com.moviebot.bot.repo;

import com.moviebot.bot.domain.AdminSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminSessionRepository extends JpaRepository<AdminSession, Long> {
}
