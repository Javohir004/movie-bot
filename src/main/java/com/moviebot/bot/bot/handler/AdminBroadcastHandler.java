package com.moviebot.bot.bot.handler;

import com.moviebot.bot.bot.AdminSessionManager;
import com.moviebot.bot.bot.BroadcastInput;
import com.moviebot.bot.bot.Keyboards;
import com.moviebot.bot.bot.TelegramSender;
import com.moviebot.bot.bot.TextFormat;
import com.moviebot.bot.domain.Broadcast;
import com.moviebot.bot.domain.PendingMovie;
import com.moviebot.bot.enums.AdminState;
import com.moviebot.bot.service.BroadcastService;
import com.moviebot.bot.service.UserService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.CopyMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.time.LocalDateTime;
import java.util.List;

// Admin: reklama tayyorlash (xabar, tugma, vaqt), rejalashtirilganlar va hisobotlar.
// Yuborishning o'zi BroadcastDispatcher'da.
@Component
public class AdminBroadcastHandler {

    private static final String BUTTON_EXAMPLES = "• @mykanal\n"
            + "• Kanalga o'tish @mykanal\n"
            + "• Saytga o'tish https://example.uz";

    private final TelegramSender sender;
    private final AdminSessionManager sessionManager;
    private final BroadcastService broadcastService;
    private final UserService userService;
    private final AdminPanelHandler panel;

    public AdminBroadcastHandler(TelegramSender sender, AdminSessionManager sessionManager,
                                 BroadcastService broadcastService, UserService userService,
                                 AdminPanelHandler panel) {
        this.sender = sender;
        this.sessionManager = sessionManager;
        this.broadcastService = broadcastService;
        this.userService = userService;
        this.panel = panel;
    }

    public boolean handleCallback(long chatId, long userId, String data) {
        if (data.equals("ADMIN:BROADCAST")) {
            sessionManager.clear(userId);
            sender.sendText(chatId, "📣 Reklama", Keyboards.broadcastMenu());
            return true;
        }

        if (data.equals("ADMIN:BROADCAST_NEW")) {
            sessionManager.clear(userId);
            sessionManager.setState(userId, AdminState.AWAITING_BROADCAST_CONTENT);
            sender.sendText(chatId, "Reklama xabarini yuboring: matn, rasm, video, GIF, fayl — istalgan turdagi xabar.\n"
                    + "U barcha foydalanuvchilarga xuddi shunday ko'rinishda yuboriladi.");
            return true;
        }

        if (data.equals("ADMIN:BROADCAST_SCHEDULED")) {
            sendScheduledBroadcasts(chatId);
            return true;
        }

        if (data.equals("ADMIN:BROADCAST_HISTORY")) {
            sendBroadcastHistory(chatId);
            return true;
        }

        if (data.startsWith("BCASTCANCEL:")) {
            Long broadcastId = Long.parseLong(data.substring("BCASTCANCEL:".length()));
            sender.sendText(chatId, broadcastService.cancel(broadcastId)
                    ? "✅ Reklama #" + broadcastId + " bekor qilindi."
                    : "Reklama #" + broadcastId + " allaqachon yuborila boshlagan yoki bekor qilingan.");
            sendScheduledBroadcasts(chatId);
            return true;
        }

        if (data.equals("BCAST:NOBTN") && sessionManager.getState(userId) == AdminState.AWAITING_BROADCAST_BUTTON) {
            showBroadcastPreview(chatId, userId);
            return true;
        }

        // holat tekshiriladi: "Yuborish" ikki marta bosilsa ikkinchisi hech narsa qilmaydi
        if (data.equals("BROADCAST:SEND")) {
            if (sessionManager.getState(userId) != AdminState.AWAITING_BROADCAST_CONFIRM) {
                sender.sendText(chatId, "Bu reklama allaqachon navbatga qo'yilgan yoki bekor qilingan.");
                return true;
            }
            scheduleBroadcast(chatId, userId, LocalDateTime.now());
            return true;
        }

        if (data.equals("BROADCAST:SCHEDULE") && sessionManager.getState(userId) == AdminState.AWAITING_BROADCAST_CONFIRM) {
            sessionManager.setState(userId, AdminState.AWAITING_BROADCAST_TIME);
            sender.sendText(chatId, "Yuborish vaqtini yozing:\n\n"
                    + "• 20:00 — bugun (vaqt o'tib ketgan bo'lsa ertaga)\n"
                    + "• 30.10 20:00\n"
                    + "• 30.10.2026 20:00");
            return true;
        }

        if (data.equals("BROADCAST:CANCEL")) {
            sessionManager.clear(userId);
            sender.sendText(chatId, "Bekor qilindi.");
            panel.showMenu(chatId);
            return true;
        }

        return false;
    }

    public boolean handleText(long chatId, long userId, AdminState state, String text) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);

        switch (state) {
            case AWAITING_BROADCAST_CONFIRM -> sender.sendText(chatId, "Iltimos, tugmalardan birini bosing.");
            case AWAITING_BROADCAST_BUTTON -> BroadcastInput.parseButton(text).ifPresentOrElse(button -> {
                pending.setBroadcastButtonText(button.text());
                pending.setBroadcastButtonUrl(button.url());
                showBroadcastPreview(chatId, userId);
            }, () -> sender.sendText(chatId, "Havolani tushunmadim. Shunday yozing:\n\n"
                    + BUTTON_EXAMPLES + "\n\n"
                    + "Havola @username, t.me/... yoki https://... bo'lishi, matn 64 belgidan oshmasligi kerak.\n"
                    + "Tugma kerak bo'lmasa, \"Tugmasiz davom etish\"ni bosing."));
            case AWAITING_BROADCAST_TIME -> BroadcastInput.parseTime(text, LocalDateTime.now()).ifPresentOrElse(
                    sendAt -> scheduleBroadcast(chatId, userId, sendAt),
                    () -> sender.sendText(chatId, "Vaqtni tushunmadim. Shunday yozing: 20:00, 30.10 20:00 yoki 30.10.2026 20:00.\n"
                            + "Vaqt kelajakda va " + BroadcastInput.MAX_DAYS_AHEAD + " kun ichida bo'lishi kerak."));
            default -> {
                return false;
            }
        }
        return true;
    }

    // Reklama kutilayotganda kelgan istalgan xabar (matn, rasm, video, GIF, fayl...)
    public void captureContent(long chatId, long userId, Message message) {
        if (message == null) {
            sender.sendText(chatId, "Iltimos, matn, rasm yoki video yuboring.");
            return;
        }

        PendingMovie pending = sessionManager.getPendingMovie(userId);
        pending.setBroadcastSourceChatId(chatId);
        pending.setBroadcastSourceMessageId(message.getMessageId());
        pending.setBroadcastButtonText(null);
        pending.setBroadcastButtonUrl(null);
        sessionManager.setState(userId, AdminState.AWAITING_BROADCAST_BUTTON);

        sender.sendText(chatId, "Reklama ostiga havola tugmasi qo'shasizmi?\n\n"
                        + "Qo'shish uchun havolani yuboring, masalan:\n\n" + BUTTON_EXAMPLES,
                Keyboards.broadcastButtonMenu());
    }

    // Reklama tayyor: adminga qanday ko'rinishini ko'rsatib, tasdiq so'raymiz
    private void showBroadcastPreview(long chatId, long userId) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        if (pending.getBroadcastSourceMessageId() == null) {
            sessionManager.clear(userId);
            sender.sendText(chatId, "Reklama xabari topilmadi. Qaytadan boshlang.");
            panel.showMenu(chatId);
            return;
        }

        CopyMessage preview = new CopyMessage();
        preview.setChatId(String.valueOf(chatId));
        preview.setFromChatId(String.valueOf(pending.getBroadcastSourceChatId()));
        preview.setMessageId(pending.getBroadcastSourceMessageId());
        if (pending.getBroadcastButtonUrl() != null) {
            preview.setReplyMarkup(Keyboards.linkButton(pending.getBroadcastButtonText(), pending.getBroadcastButtonUrl()));
        }
        sender.send(preview);

        sessionManager.setState(userId, AdminState.AWAITING_BROADCAST_CONFIRM);
        int count = userService.getAllActiveUserIds().size();
        sender.sendText(chatId, "☝️ Reklama shunday ko'rinadi. U " + count + " ta foydalanuvchiga yuboriladi.\n\n"
                        + "⚠️ Yuborilib bo'lguncha asl xabarni o'chirmang — reklama undan nusxalanadi.",
                Keyboards.broadcastConfirmMenu());
    }

    private void scheduleBroadcast(long chatId, long userId, LocalDateTime sendAt) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        if (pending.getBroadcastSourceMessageId() == null) {
            sessionManager.clear(userId);
            sender.sendText(chatId, "Reklama xabari topilmadi. Qaytadan boshlang.");
            panel.showMenu(chatId);
            return;
        }

        Broadcast broadcast = broadcastService.schedule(userId,
                pending.getBroadcastSourceChatId(),
                pending.getBroadcastSourceMessageId(),
                pending.getBroadcastButtonText(),
                pending.getBroadcastButtonUrl(),
                sendAt);
        sessionManager.clear(userId);

        boolean now = !sendAt.isAfter(LocalDateTime.now());
        sender.sendText(chatId, now
                ? "✅ Reklama #" + broadcast.getId() + " navbatga qo'yildi, bir necha soniyada yuborila boshlaydi."
                : "✅ Reklama #" + broadcast.getId() + " " + sendAt.format(TextFormat.DATE_TIME) + " da yuboriladi.\n"
                + "Bekor qilish: 📣 Reklama → 🗓 Rejalashtirilganlar.");
        panel.showMenu(chatId);
    }

    private void sendScheduledBroadcasts(long chatId) {
        List<Broadcast> scheduled = broadcastService.findScheduled();
        StringBuilder text = new StringBuilder("🗓 <b>Rejalashtirilgan reklamalar</b>\n\n");

        if (scheduled.isEmpty()) {
            text.append("Hozircha yo'q.");
        } else {
            for (Broadcast b : scheduled) {
                text.append("#").append(b.getId()).append(" · ").append(b.getSendAt().format(TextFormat.DATE_TIME));
                if (b.getButtonText() != null) {
                    text.append(" · 🔘 ").append(TextFormat.escape(b.getButtonText()));
                }
                text.append("\n");
            }
            text.append("\nBekor qilish uchun pastdagi tugmani bosing.");
        }

        sender.sendHtml(chatId, text.toString(), Keyboards.scheduledBroadcastsMenu(scheduled, TextFormat.DATE_TIME));
    }

    private void sendBroadcastHistory(long chatId) {
        List<Broadcast> recent = broadcastService.findRecentFinished(10);
        StringBuilder text = new StringBuilder("📈 <b>Oxirgi reklamalar</b>\n\n");

        if (recent.isEmpty()) {
            text.append("Hozircha yuborilgan reklama yo'q.");
        }

        for (Broadcast b : recent) {
            LocalDateTime when = b.getStartedAt() != null ? b.getStartedAt() : b.getSendAt();
            text.append("#").append(b.getId()).append(" · ").append(when.format(TextFormat.DATE_TIME)).append(" · ")
                    .append(switch (b.getStatus()) {
                        case SENDING -> "⏳ yuborilmoqda";
                        case INTERRUPTED -> "⚠️ to'xtab qolgan";
                        default -> "✅ yakunlangan";
                    })
                    .append("\n    ✅ ").append(b.getSent())
                    .append("  🚫 ").append(b.getBlocked())
                    .append("  ❌ ").append(b.getFailed())
                    .append("  (jami ").append(b.getTotal()).append(")\n\n");
        }

        sender.sendHtml(chatId, text.toString(), Keyboards.backToBroadcastMenu());
    }
}
