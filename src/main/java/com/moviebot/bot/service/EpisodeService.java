package com.moviebot.bot.service;

import com.moviebot.bot.domain.Episode;
import com.moviebot.bot.domain.Season;
import com.moviebot.bot.repo.EpisodeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EpisodeService {

    private final EpisodeRepository episodeRepository;

    public EpisodeService(EpisodeRepository episodeRepository) {
        this.episodeRepository = episodeRepository;
    }

    public Episode create(Season season, int episodeNumber, String fileId) {
        Episode episode = Episode.builder()
                .season(season)
                .episodeNumber(episodeNumber)
                .fileId(fileId)
                .build();

        return episodeRepository.save(episode);
    }

    public List<Episode> getEpisodesForSeason(Long seasonId) {
        return episodeRepository.findBySeasonIdOrderByEpisodeNumber(seasonId);
    }

    public Episode getById(Long id) {
        return episodeRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Episode topilmadi: id=" + id));
    }

    public Optional<Episode> findBySeasonAndEpisodeNumber(Long seasonId, int episodeNumber) {
        return episodeRepository.findBySeasonIdAndEpisodeNumber(seasonId, episodeNumber);
    }
}