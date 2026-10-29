package com.moviebot.bot.service;

import com.moviebot.bot.domain.ChannelJoin;
import com.moviebot.bot.domain.RequiredChannel;
import com.moviebot.bot.repo.ChannelJoinRepository;
import com.moviebot.bot.repo.RequiredChannelRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class RequiredChannelService {

    private final RequiredChannelRepository requiredChannelRepository;
    private final ChannelJoinRepository channelJoinRepository;

    public RequiredChannelService(RequiredChannelRepository requiredChannelRepository,
                                  ChannelJoinRepository channelJoinRepository) {
        this.requiredChannelRepository = requiredChannelRepository;
        this.channelJoinRepository = channelJoinRepository;
    }

    public boolean exists(String username, String chatId) {
        if (username != null && requiredChannelRepository.findByChannelUsernameIgnoreCase(username).isPresent()) {
            return true;
        }
        return chatId != null && requiredChannelRepository.findByChatId(chatId).isPresent();
    }

    // username yoki chatId dan biri beriladi; expiresAt bo'sh bo'lsa muddatsiz
    public RequiredChannel add(String username, String chatId, String title, String inviteLink,
                               boolean joinRequest, LocalDateTime expiresAt) {
        return requiredChannelRepository.save(RequiredChannel.builder()
                .channelUsername(username)
                .chatId(chatId)
                .title(title)
                .inviteLink(inviteLink)
                .joinRequest(joinRequest)
                .expiresAt(expiresAt)
                .active(true)
                .build());
    }

    // obuna talab qilinadigan kanallar: faol va muddati tugamagan
    public List<RequiredChannel> getActiveChannels() {
        return requiredChannelRepository.findEnforced(LocalDateTime.now());
    }

    public List<RequiredChannel> findExpired() {
        return requiredChannelRepository.findExpired(LocalDateTime.now());
    }

    public Optional<RequiredChannel> findById(Long id) {
        return requiredChannelRepository.findById(id);
    }

    public Optional<RequiredChannel> findByChatId(String chatId) {
        return requiredChannelRepository.findByChatId(chatId);
    }

    public void remove(Long id) {
        requiredChannelRepository.deleteById(id);
    }

    // bot orqali qo'shilganni bir marta sanaydi; yangi yozuv bo'lsa true
    public boolean recordJoin(Long channelId, Long userId, boolean viaRequest) {
        if (channelJoinRepository.existsByChannelIdAndUserId(channelId, userId)) {
            return false;
        }
        channelJoinRepository.save(ChannelJoin.builder()
                .channelId(channelId)
                .userId(userId)
                .viaRequest(viaRequest)
                .createdAt(LocalDateTime.now())
                .build());
        return true;
    }

    public boolean hasJoinRequest(Long channelId, Long userId) {
        return channelJoinRepository.existsByChannelIdAndUserIdAndViaRequestTrue(channelId, userId);
    }

    public long joinCount(Long channelId) {
        return channelJoinRepository.countByChannelId(channelId);
    }
}
