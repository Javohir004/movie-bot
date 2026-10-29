package com.moviebot.bot.bot;

import com.moviebot.bot.domain.Broadcast;
import com.moviebot.bot.service.BroadcastService;
import com.moviebot.bot.service.UserService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.CopyMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

// Navbatdagi reklamalarni vaqti kelganda yuboradi. Bir vaqtda bitta reklama yuboriladi,
// alohida thread'da, shuning uchun bot bu paytda ham hammaga javob berib turadi.
@Slf4j
@Component
public class BroadcastDispatcher {

    // Telegram limiti ~30 xabar/soniya, biz biroz past ushlaymiz
    private static final long DELAY_MS = 40;
    // shuncha xabardan keyin natija bazaga yoziladi (bot o'chib qolsa ham qancha ketgani ma'lum bo'lsin)
    private static final int PROGRESS_EVERY = 200;

    private final TelegramSender sender;
    private final BroadcastService broadcastService;
    private final UserService userService;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public BroadcastDispatcher(TelegramSender sender, BroadcastService broadcastService, UserService userService) {
        this.sender = sender;
        this.broadcastService = broadcastService;
        this.userService = userService;
    }

    // Bot yuborish o'rtasida o'chib qolgan bo'lsa: qayta yubormaymiz (odamlarga takror ketadi), faqat xabar beramiz
    @EventListener(ApplicationReadyEvent.class)
    public void markInterruptedBroadcasts() {
        for (Broadcast b : broadcastService.findSending()) {
            if (broadcastService.markInterrupted(b.getId())) {
                sender.sendText(b.getCreatedBy(), "⚠️ Reklama #" + b.getId() + " yuborilayotganda bot qayta ishga tushdi va u to'xtab qoldi.\n"
                        + "✅ Yuborilgan: ~" + b.getSent() + " / " + b.getTotal() + "\n"
                        + "Qolganlarga takror ketmasligi uchun avtomatik davom ettirilmadi.");
            }
        }
    }

    @Scheduled(initialDelay = 15_000, fixedDelay = 10_000)
    public void dispatchDue() {
        if (!running.compareAndSet(false, true)) return;

        boolean started = false;
        try {
            Broadcast broadcast = broadcastService.findNextDue().orElse(null);
            if (broadcast == null) return;

            List<Long> targets = userService.getAllActiveUserIds();

            // shu orada bekor qilingan bo'lsa yuborilmaydi
            if (!broadcastService.markSending(broadcast.getId(), targets.size())) return;

            sender.sendText(broadcast.getCreatedBy(), "⏳ Reklama #" + broadcast.getId() + " yuborilmoqda: "
                    + targets.size() + " ta foydalanuvchi. Tugagach hisobot yuboraman.");
            executor.submit(() -> send(broadcast, targets));
            started = true;
        } finally {
            // yuborish boshlanmagan bo'lsa bayroqni qaytaramiz; boshlangan bo'lsa send() o'zi qaytaradi
            if (!started) running.set(false);
        }
    }

    private void send(Broadcast broadcast, List<Long> targets) {
        int sent = 0;
        int failed = 0;
        List<Long> blocked = new ArrayList<>();
        LocalDateTime started = LocalDateTime.now();

        try {
            int processed = 0;
            for (Long targetId : targets) {
                switch (copyWithRetry(broadcast, targetId)) {
                    case SENT -> sent++;
                    case BLOCKED -> blocked.add(targetId);
                    case FAILED -> failed++;
                }

                if (++processed % PROGRESS_EVERY == 0) {
                    broadcastService.saveProgress(broadcast.getId(), sent, blocked.size(), failed);
                }
                Thread.sleep(DELAY_MS);
            }
        } catch (InterruptedException e) {
            // bot o'chirilmoqda: holat SENDING qoladi, keyingi ishga tushishda INTERRUPTED bo'ladi
            Thread.currentThread().interrupt();
            broadcastService.saveProgress(broadcast.getId(), sent, blocked.size(), failed);
            running.set(false);
            return;
        } catch (RuntimeException e) {
            log.error("Reklama #{} yuborishda kutilmagan xato", broadcast.getId(), e);
        }

        try {
            userService.deactivate(blocked);
        } catch (RuntimeException e) {
            log.error("Bloklagan foydalanuvchilarni belgilashda xato", e);
        }

        try {
            broadcastService.finish(broadcast.getId(), sent, blocked.size(), failed);
        } catch (RuntimeException e) {
            log.error("Reklama #{} natijasini saqlashda xato", broadcast.getId(), e);
        } finally {
            running.set(false);
        }

        long minutes = Duration.between(started, LocalDateTime.now()).toMinutes();
        sender.sendText(broadcast.getCreatedBy(), "📣 Reklama #" + broadcast.getId() + " yakunlandi\n\n"
                + "✅ Yuborildi: " + sent + "\n"
                + "🚫 Botni bloklagan: " + blocked.size() + " (endi ularga yuborilmaydi)\n"
                + "❌ Xato: " + failed + "\n"
                + "⏱ Davomiyligi: " + (minutes < 1 ? "1 daqiqadan kam" : minutes + " daqiqa"));
    }

    private enum SendResult { SENT, BLOCKED, FAILED }

    private SendResult copyWithRetry(Broadcast broadcast, Long targetId) throws InterruptedException {
        CopyMessage copyMessage = new CopyMessage();
        copyMessage.setChatId(String.valueOf(targetId));
        copyMessage.setFromChatId(String.valueOf(broadcast.getSourceChatId()));
        copyMessage.setMessageId(broadcast.getSourceMessageId());
        if (broadcast.getButtonUrl() != null) {
            copyMessage.setReplyMarkup(Keyboards.linkButton(broadcast.getButtonText(), broadcast.getButtonUrl()));
        }

        try {
            sender.execute(copyMessage);
            return SendResult.SENT;
        } catch (TelegramApiRequestException e) {
            // 403 Forbidden: bot bloklangan, akkaunt o'chirilgan yoki foydalanuvchi botni hech ochmagan
            if (e.getErrorCode() != null && e.getErrorCode() == 403) {
                return SendResult.BLOCKED;
            }
            // 429 Too Many Requests: Telegram aytgan vaqtcha kutib, bir marta qayta urinamiz
            if (e.getErrorCode() != null && e.getErrorCode() == 429
                    && e.getParameters() != null && e.getParameters().getRetryAfter() != null) {
                Thread.sleep(e.getParameters().getRetryAfter() * 1000L);
                try {
                    sender.execute(copyMessage);
                    return SendResult.SENT;
                } catch (TelegramApiException retryError) {
                    return SendResult.FAILED;
                }
            }
            return SendResult.FAILED;
        } catch (TelegramApiException e) {
            return SendResult.FAILED;
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
