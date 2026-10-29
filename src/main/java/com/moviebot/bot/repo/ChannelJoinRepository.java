package com.moviebot.bot.repo;

import com.moviebot.bot.domain.ChannelJoin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChannelJoinRepository extends JpaRepository<ChannelJoin, Long> {

    boolean existsByChannelIdAndUserId(Long channelId, Long userId);

    boolean existsByChannelIdAndUserIdAndViaRequestTrue(Long channelId, Long userId);

    long countByChannelId(Long channelId);
}
