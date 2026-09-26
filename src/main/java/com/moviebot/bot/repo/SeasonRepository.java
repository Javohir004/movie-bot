package com.moviebot.bot.repo;

import com.moviebot.bot.domain.Episode;
import com.moviebot.bot.domain.Season;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeasonRepository extends JpaRepository<Season, Long> {

}
