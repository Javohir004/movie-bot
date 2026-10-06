package com.moviebot.bot.repo;

import com.moviebot.bot.domain.RequiredChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RequiredChannelRepository extends JpaRepository<RequiredChannel, Long> {

    List<RequiredChannel> findByActiveTrue();
    Optional<RequiredChannel> findByChannelUsernameIgnoreCase(String username);

}