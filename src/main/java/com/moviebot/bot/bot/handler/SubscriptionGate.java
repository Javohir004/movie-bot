package com.moviebot.bot.bot.handler;

import com.moviebot.bot.bot.Keyboards;
import com.moviebot.bot.bot.TelegramSender;
import com.moviebot.bot.domain.RequiredChannel;
import com.moviebot.bot.service.RequiredChannelService;
import com.moviebot.bot.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.objects.ChatJoinRequest;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Majburiy obuna: kanallarga a'zolikni tekshirish, so'rov ko'rsatish va bot orqali qo'shilganlarni sanash
@Slf4j
@Component
public class SubscriptionGate {

    private final TelegramSender sender;
    private final RequiredChannelService requiredChannelService;
    private final UserService userService;
    private final BotTexts texts;

    // Kanalga bot ko'rsatganidan keyin qo'shilganlarni sanash uchun: kimga qaysi kanallar ko'rsatildi
    private final Map<Long, Set<Long>> promptedChannels = new ConcurrentHashMap<>();

    public SubscriptionGate(TelegramSender sender, RequiredChannelService requiredChannelService,
                            UserService userService, BotTexts texts) {
        this.sender = sender;
        this.requiredChannelService = requiredChannelService;
        this.userService = userService;
        this.texts = texts;
    }

    // eski xabardagi tugmalar orqali ham obunasiz kontent ko'rib bo'lmasin
    public static boolean isContentCallback(String data) {
        return data.startsWith("WATCHMOVIE:")
                || data.startsWith("MOVIE:")
                || data.startsWith("SERIES:")
                || data.startsWith("SEASON:")
                || data.startsWith("EPISODE:");
    }

    // obuna bo'lmagan bo'lsa kanallar ro'yxatini yuboradi va false qaytaradi
    public boolean ensureSubscribed(long chatId, long userId) {
        if (userService.isAdmin(userId)) {
            return true;
        }

        List<RequiredChannel> unjoined = getUnjoinedChannels(userId);

        if (unjoined.isEmpty()) {
            return true;
        }

        sender.sendText(chatId, texts.t(chatId, "sub.prompt"), Keyboards.subscriptionPromptMenu(unjoined, texts.tr(chatId)));
        return false;
    }

    private List<RequiredChannel> getUnjoinedChannels(long userId) {
        List<RequiredChannel> channels = requiredChannelService.getActiveChannels();
        List<RequiredChannel> unjoined = new ArrayList<>();
        Set<Long> prompted = promptedChannels.getOrDefault(userId, Set.of());

        for (RequiredChannel channel : channels) {
            if (!isMemberOf(channel, userId)) {
                unjoined.add(channel);
            } else if (prompted.contains(channel.getId())) {
                // bot so'raganidan keyin qo'shildi
                requiredChannelService.recordJoin(channel.getId(), userId, false);
            }
        }

        if (unjoined.isEmpty()) {
            promptedChannels.remove(userId);
        } else {
            Set<Long> ids = new HashSet<>();
            unjoined.forEach(c -> ids.add(c.getId()));
            promptedChannels.put(userId, ids);
        }

        return unjoined;
    }

    private boolean isMemberOf(RequiredChannel channel, long userId) {
        // zayavkali kanalda zayavka yuborganining o'zi yetarli (API'ga murojaat qilmasdan)
        if (channel.isJoinRequest() && requiredChannelService.hasJoinRequest(channel.getId(), userId)) {
            return true;
        }

        try {
            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(channel.chatIdentifier());
            getChatMember.setUserId(userId);
            ChatMember member = sender.execute(getChatMember);
            String status = member.getStatus();
            return !"left".equals(status) && !"kicked".equals(status);
        } catch (TelegramApiException e) {
            // tekshira olmasak foydalanuvchini to'smaymiz, lekin admin bilishi uchun logga yozamiz
            log.warn("{} kanalida a'zolikni tekshirib bo'lmadi (bot admin emasmi?): {}", channel.chatIdentifier(), e.getMessage());
            return true;
        }
    }

    // Zayavkali (yopiq) kanalga zayavka kelganda: shu foydalanuvchi obuna bo'lgan hisoblanadi
    public void handleJoinRequest(ChatJoinRequest request) {
        if (request.getChat() == null || request.getUser() == null) return;

        String channelChatId = String.valueOf(request.getChat().getId());
        requiredChannelService.findByChatId(channelChatId)
                .filter(RequiredChannel::isJoinRequest)
                .ifPresent(channel -> requiredChannelService.recordJoin(channel.getId(), request.getUser().getId(), true));
    }
}
