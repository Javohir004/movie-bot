package com.moviebot.bot.service;

import com.moviebot.bot.domain.RequiredChannel;
import com.moviebot.bot.repo.RequiredChannelRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RequiredChannelService {

    private final RequiredChannelRepository requiredChannelRepository;

    public RequiredChannelService(RequiredChannelRepository requiredChannelRepository) {
        this.requiredChannelRepository = requiredChannelRepository;
    }

    public void addChannel(String username) {
        if (requiredChannelRepository.findByChannelUsernameIgnoreCase(username).isEmpty()) {
            RequiredChannel channel = RequiredChannel.builder()
                    .channelUsername(username)
                    .active(true)
                    .build();
            requiredChannelRepository.save(channel);
        }
    }

    public List<RequiredChannel> getActiveChannels() {
        return requiredChannelRepository.findByActiveTrue();
    }

    public void remove(Long id) {
        requiredChannelRepository.deleteById(id);
    }
}