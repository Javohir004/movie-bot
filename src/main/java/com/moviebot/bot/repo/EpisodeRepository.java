package com.moviebot.bot.repo;

import com.moviebot.bot.domain.Episode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EpisodeRepository extends JpaRepository<Episode, Long> {

    List<Episode> findBySeasonIdOrderByEpisodeNumber(Long seasonId);

    Optional<Episode> findBySeasonIdAndEpisodeNumber(Long seasonId, int episodeNumber);
}
