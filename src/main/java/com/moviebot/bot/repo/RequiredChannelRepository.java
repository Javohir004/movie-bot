package com.moviebot.bot.repo;

import com.moviebot.bot.domain.RequiredChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RequiredChannelRepository extends JpaRepository<RequiredChannel, Long> {

    List<RequiredChannel> findByActiveTrue();

    Optional<RequiredChannel> findByChannelUsernameIgnoreCase(String username);

    Optional<RequiredChannel> findByChatId(String chatId);

    // muddati tugamagan faol kanallar
    @Query("select c from RequiredChannel c where c.active = true and (c.expiresAt is null or c.expiresAt > :now) order by c.id")
    List<RequiredChannel> findEnforced(LocalDateTime now);

    @Query("select c from RequiredChannel c where c.active = true and c.expiresAt is not null and c.expiresAt <= :now")
    List<RequiredChannel> findExpired(LocalDateTime now);
}
