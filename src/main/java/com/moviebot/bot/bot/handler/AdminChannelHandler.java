package com.moviebot.bot.bot.handler;

import com.moviebot.bot.bot.AdminSessionManager;
import com.moviebot.bot.bot.Keyboards;
import com.moviebot.bot.bot.TelegramSender;
import com.moviebot.bot.bot.TextFormat;
import com.moviebot.bot.domain.PendingMovie;
import com.moviebot.bot.domain.RequiredChannel;
import com.moviebot.bot.enums.AdminState;
import com.moviebot.bot.service.RequiredChannelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.groupadministration.CreateChatInviteLink;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.util.List;

// Admin: homiy (majburiy) kanallarni qo'shish, muddat belgilash va olib tashlash
@Slf4j
@Component
public class AdminChannelHandler {

    private final TelegramSender sender;
    private final AdminSessionManager sessionManager;
    private final RequiredChannelService requiredChannelService;

    public AdminChannelHandler(TelegramSender sender, AdminSessionManager sessionManager,
                               RequiredChannelService requiredChannelService) {
        this.sender = sender;
        this.sessionManager = sessionManager;
        this.requiredChannelService = requiredChannelService;
    }

    public boolean handleCallback(long chatId, long userId, String data) {
        if (data.equals("ADMIN:CHANNELS")) {
            sendChannelList(chatId);
            return true;
        }

        if (data.equals("ADMIN:ADD_CHANNEL")) {
            sessionManager.clear(userId);
            sessionManager.setState(userId, AdminState.AWAITING_CHANNEL_USERNAME);
            sender.sendText(chatId, "Kanalni yuboring:\n\n"
                    + "• Ochiq kanal — username (masalan @mychannel)\n"
                    + "• Yopiq kanal — kanal ID'si (masalan -1001234567890). Bot o'zi zayavkali havola yaratadi, "
                    + "zayavka yuborgan foydalanuvchi obuna bo'lgan hisoblanadi.\n\n"
                    + "Yopiq kanal ID'sini bilish: kanalni web.telegram.org/a saytida oching, manzil qatoridagi # dan keyingi raqam.\n\n"
                    + "⚠️ Bot shu kanalda admin bo'lishi shart (yopiq kanalda \"Foydalanuvchilarni taklif qilish\" huquqi bilan).");
            return true;
        }

        if (data.startsWith("CHDUR:") && sessionManager.getState(userId) == AdminState.AWAITING_CHANNEL_DURATION) {
            finishAddChannel(chatId, userId, Integer.parseInt(data.substring("CHDUR:".length())));
            return true;
        }

        if (data.startsWith("REMOVECHANNEL:")) {
            Long channelId = Long.parseLong(data.substring("REMOVECHANNEL:".length()));
            requiredChannelService.findById(channelId).ifPresentOrElse(
                    channel -> sender.sendText(chatId,
                            channel.displayName() + " kanalini olib tashlaysizmi?\n\n"
                                    + "👥 Bot orqali qo'shilganlar: " + requiredChannelService.joinCount(channelId)
                                    + "\n\nO'chirilgandan keyin bu hisob ham o'chadi.",
                            Keyboards.channelRemoveConfirmMenu(channelId)),
                    () -> sendChannelList(chatId));
            return true;
        }

        if (data.startsWith("RMCHCONF:")) {
            Long channelId = Long.parseLong(data.substring("RMCHCONF:".length()));
            requiredChannelService.findById(channelId).ifPresent(channel -> {
                long joins = requiredChannelService.joinCount(channelId);
                requiredChannelService.remove(channelId);
                sender.sendText(chatId, "✅ " + channel.displayName() + " olib tashlandi.\n👥 Bot orqali qo'shilganlar: " + joins);
            });
            sendChannelList(chatId);
            return true;
        }

        return false;
    }

    public boolean handleText(long chatId, long userId, AdminState state, String text) {
        switch (state) {
            case AWAITING_CHANNEL_USERNAME -> handleChannelInput(chatId, userId, text.trim());
            case AWAITING_CHANNEL_DURATION -> {
                try {
                    int days = Integer.parseInt(text.trim());
                    if (days < 1 || days > 3650) throw new NumberFormatException();
                    finishAddChannel(chatId, userId, days);
                } catch (NumberFormatException e) {
                    sender.sendText(chatId, "Kunlar sonini raqam bilan yozing (masalan 14) yoki tugmalardan birini tanlang.");
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void handleChannelInput(long chatId, long userId, String input) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);

        if (input.matches("-100\\d{5,}")) {
            String problem = checkBotIsChannelAdmin(input);
            if (problem != null) {
                sender.sendText(chatId, "❌ Kanal qo'shilmadi: " + problem + "\n\nBotni kanalga admin qilib, qaytadan yuboring:");
                return;
            }
            if (requiredChannelService.exists(null, input)) {
                sender.sendText(chatId, "Bu kanal allaqachon ro'yxatda. Boshqa kanal yuboring:");
                return;
            }

            String link = createJoinRequestLink(input);
            if (link == null) {
                sender.sendText(chatId, "❌ Havola yaratib bo'lmadi. Botga kanalda \"Foydalanuvchilarni taklif qilish\" huquqini bering va ID'ni qaytadan yuboring:");
                return;
            }

            pending.setChannelChatId(input);
            pending.setChannelUsername(null);
            pending.setChannelInviteLink(link);
            pending.setChannelJoinRequest(true);
            pending.setChannelTitle(fetchChatTitle(input));
        } else {
            String cleaned = input
                    .replace("https://t.me/", "")
                    .replace("http://t.me/", "")
                    .replace("t.me/", "");
            if (cleaned.startsWith("@")) {
                cleaned = cleaned.substring(1);
            }

            if (!cleaned.matches("[A-Za-z0-9_]{4,32}")) {
                sender.sendText(chatId, "Username yoki ID noto'g'ri. Masalan: @mychannel yoki -1001234567890. Qaytadan yuboring:");
                return;
            }

            // bot kanalda admin bo'lmasa, a'zolikni tekshira olmaydi va obuna talabi jimgina ishlamay qoladi
            String problem = checkBotIsChannelAdmin("@" + cleaned);
            if (problem != null) {
                sender.sendText(chatId, "❌ @" + cleaned + " qo'shilmadi: " + problem
                        + "\n\nBotni kanalga admin qilib, qaytadan yuboring:");
                return;
            }
            if (requiredChannelService.exists(cleaned, null)) {
                sender.sendText(chatId, "Bu kanal allaqachon ro'yxatda. Boshqa kanal yuboring:");
                return;
            }

            pending.setChannelUsername(cleaned);
            pending.setChannelChatId(null);
            pending.setChannelInviteLink(null);
            pending.setChannelJoinRequest(false);
            pending.setChannelTitle(fetchChatTitle("@" + cleaned));
        }

        sessionManager.setState(userId, AdminState.AWAITING_CHANNEL_DURATION);
        String name = pending.getChannelTitle() != null ? pending.getChannelTitle()
                : (pending.getChannelUsername() != null ? "@" + pending.getChannelUsername() : pending.getChannelChatId());
        sender.sendText(chatId, "\"" + name + "\" uchun majburiy obuna muddati qancha?\n\nTugmani tanlang yoki kunlar sonini yozing (masalan 14):",
                Keyboards.channelDurationMenu());
    }

    // days = 0 bo'lsa muddatsiz
    private void finishAddChannel(long chatId, long userId, int days) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);

        if (pending.getChannelUsername() == null && pending.getChannelChatId() == null) {
            sessionManager.clear(userId);
            sender.sendText(chatId, "Jarayon topilmadi, kanalni qaytadan qo'shing.");
            sendChannelList(chatId);
            return;
        }

        LocalDateTime expiresAt = days == 0 ? null : LocalDateTime.now().plusDays(days);
        RequiredChannel channel = requiredChannelService.add(
                pending.getChannelUsername(),
                pending.getChannelChatId(),
                pending.getChannelTitle(),
                pending.getChannelInviteLink(),
                pending.isChannelJoinRequest(),
                expiresAt);

        sessionManager.clear(userId);
        sender.sendText(chatId, "✅ " + channel.displayName() + " qo'shildi. "
                + (expiresAt == null ? "Muddatsiz." : "Muddat: " + expiresAt.format(TextFormat.DATE_TIME) + " gacha."));
        sendChannelList(chatId);
    }

    private void sendChannelList(long chatId) {
        List<RequiredChannel> channels = requiredChannelService.getActiveChannels();
        StringBuilder text = new StringBuilder("📢 <b>Majburiy kanallar</b>\n\n");

        if (channels.isEmpty()) {
            text.append("Hozircha kanal qo'shilmagan.");
        }

        for (int i = 0; i < channels.size(); i++) {
            RequiredChannel c = channels.get(i);
            text.append(i + 1).append(". ").append(TextFormat.escape(c.displayName()));
            if (c.getTitle() != null && c.getChannelUsername() != null) {
                text.append(" (@").append(TextFormat.escape(c.getChannelUsername())).append(")");
            }
            if (c.isJoinRequest()) {
                text.append(" 🔒 zayavka");
            }
            text.append("\n    👥 Bot orqali: ").append(requiredChannelService.joinCount(c.getId()));
            text.append("\n    ⏳ ").append(describeExpiry(c.getExpiresAt())).append("\n\n");
        }

        sender.sendHtml(chatId, text.toString(), Keyboards.channelListMenu(channels));
    }

    private String describeExpiry(LocalDateTime expiresAt) {
        if (expiresAt == null) return "Muddatsiz";
        long daysLeft = Math.max(0, java.time.Duration.between(LocalDateTime.now(), expiresAt).toDays());
        return expiresAt.format(TextFormat.DATE_TIME) + " gacha (" + daysLeft + " kun qoldi)";
    }

    // chatIdentifier: "@username" yoki "-100..."; null qaytarsa hammasi joyida, aks holda muammo matni
    private String checkBotIsChannelAdmin(String chatIdentifier) {
        try {
            Long botId = sender.execute(new GetMe()).getId();

            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(chatIdentifier);
            getChatMember.setUserId(botId);
            String status = sender.execute(getChatMember).getStatus();

            if ("administrator".equals(status) || "creator".equals(status)) {
                return null;
            }
            return "bot bu kanalda admin emas.";
        } catch (TelegramApiException e) {
            return "kanal topilmadi yoki bot unga qo'shilmagan.";
        }
    }

    private String fetchChatTitle(String chatIdentifier) {
        try {
            return sender.execute(GetChat.builder().chatId(chatIdentifier).build()).getTitle();
        } catch (TelegramApiException e) {
            log.warn("{} kanal nomini olib bo'lmadi: {}", chatIdentifier, e.getMessage());
            return null;
        }
    }

    // yopiq kanal uchun zayavkali havola; bot "Foydalanuvchilarni taklif qilish" huquqiga ega bo'lishi kerak
    private String createJoinRequestLink(String chatId) {
        try {
            return sender.execute(CreateChatInviteLink.builder()
                    .chatId(chatId)
                    .name("Bot orqali")
                    .createsJoinRequest(true)
                    .build()).getInviteLink();
        } catch (TelegramApiException e) {
            log.warn("{} uchun havola yaratib bo'lmadi: {}", chatId, e.getMessage());
            return null;
        }
    }
}
