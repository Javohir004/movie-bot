package com.moviebot.bot.bot;

import com.moviebot.bot.domain.RequiredChannel;
import com.moviebot.bot.service.RequiredChannelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Muddati tugagan homiy kanallarni olib tashlaydi va adminlarga yakuniy natijani yuboradi.
// Obuna talabi muddat tugashi bilan darhol to'xtaydi (getActiveChannels muddatni tekshiradi),
// bu job faqat ro'yxatni tozalaydi va xabar beradi.
@Slf4j
@Component
public class ChannelExpiryJob {

    private final RequiredChannelService requiredChannelService;
    private final MovieBot movieBot;

    public ChannelExpiryJob(RequiredChannelService requiredChannelService, MovieBot movieBot) {
        this.requiredChannelService = requiredChannelService;
        this.movieBot = movieBot;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 5 * 60_000)
    public void removeExpiredChannels() {
        for (RequiredChannel channel : requiredChannelService.findExpired()) {
            try {
                long joins = requiredChannelService.joinCount(channel.getId());
                requiredChannelService.remove(channel.getId());
                movieBot.notifyAdmins("⏳ Homiy kanal muddati tugadi va olib tashlandi: " + channel.displayName()
                        + "\n👥 Bot orqali qo'shilganlar: " + joins);
            } catch (Exception e) {
                log.error("Muddati tugagan kanalni olib tashlashda xato: id={}", channel.getId(), e);
            }
        }
    }
}
