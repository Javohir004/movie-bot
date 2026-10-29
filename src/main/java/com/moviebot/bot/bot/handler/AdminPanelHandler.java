package com.moviebot.bot.bot.handler;

import com.moviebot.bot.bot.Keyboards;
import com.moviebot.bot.bot.TelegramSender;
import com.moviebot.bot.domain.AdminInvite;
import com.moviebot.bot.domain.User;
import com.moviebot.bot.enums.InviteStatus;
import com.moviebot.bot.service.AdminInviteService;
import com.moviebot.bot.service.StatsService;
import com.moviebot.bot.service.UserService;
import com.moviebot.bot.bot.AdminSessionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

// Admin menyusi, adminlar ro'yxati, taklif havolalari va statistika
@Component
public class AdminPanelHandler {

    private final TelegramSender sender;
    private final UserService userService;
    private final AdminInviteService adminInviteService;
    private final StatsService statsService;
    private final AdminSessionManager sessionManager;
    private final String username;
    private final Long rootAdminId;

    public AdminPanelHandler(TelegramSender sender,
                             UserService userService,
                             AdminInviteService adminInviteService,
                             StatsService statsService,
                             AdminSessionManager sessionManager,
                             @Value("${bot.username}") String username,
                             @Value("${bot.root-admin-id}") Long rootAdminId) {
        this.sender = sender;
        this.userService = userService;
        this.adminInviteService = adminInviteService;
        this.statsService = statsService;
        this.sessionManager = sessionManager;
        this.username = username;
        this.rootAdminId = rootAdminId;
    }

    public boolean handleCallback(long chatId, long userId, String data) {
        if (data.equals("ADMIN:ADD_ADMIN")) {
            AdminInvite invite = adminInviteService.createInvite(userId);
            String link = "https://t.me/" + username + "?start=" + invite.getToken();

            sender.sendText(chatId, "Yangi admin qo'shish uchun havola (10 daqiqa amal qiladi):\n\n"
                    + link
                    + "\n\nUshbu havolani kerakli odamga yuboring. U bosgandan keyin sizga tasdiqlash so'rovi keladi.");
            return true;
        }

        if (data.equals("ADMIN:LIST_ADMINS")) {
            sendAdminList(chatId);
            return true;
        }

        if (data.equals("NOOP")) {
            return true;
        }

        if (data.startsWith("REMOVEADMIN:")) {
            Long targetId = Long.parseLong(data.substring("REMOVEADMIN:".length()));
            handleRemoveAdmin(chatId, userId, targetId);
            return true;
        }

        if (data.equals("ADMIN:STATS")) {
            sender.sendHtml(chatId, statsService.buildReport(), Keyboards.statsMenu());
            return true;
        }

        if (data.equals("ADMIN:MENU")) {
            sessionManager.clear(userId);
            showMenu(chatId);
            return true;
        }

        return false;
    }

    public void showMenu(long chatId) {
        sender.sendText(chatId, "Admin panel:", Keyboards.adminMenu());
    }

    // Muddati tugagan kanal haqida va boshqa tizim xabarlari uchun
    public void notifyAdmins(String text) {
        for (User admin : userService.getAllAdmins()) {
            sender.sendText(admin.getUserId(), text);
        }
    }

    private void sendAdminList(long chatId) {
        List<User> admins = userService.getAllAdmins();
        sender.sendText(chatId, "Adminlar ro'yxati:", Keyboards.adminListMenu(admins, rootAdminId));
    }

    private void handleRemoveAdmin(long chatId, long userId, Long targetId) {
        if (targetId.equals(rootAdminId)) {
            sender.sendText(chatId, "Asosiy adminni olib tashlab bo'lmaydi.");
            return;
        }

        if (targetId == userId) {
            sender.sendText(chatId, "O'zingizni adminlikdan olib tashlay olmaysiz.");
            return;
        }

        userService.makeUser(targetId);
        sender.sendText(chatId, "✅ Foydalanuvchi adminlikdan olindi.");
        sendAdminList(chatId);
    }

    // ---- taklif havolasi: /start <token> bilan kelgan foydalanuvchi va havola egasi ----

    public void handleInviteStart(long chatId, long userId, String firstName, String token) {
        Optional<AdminInvite> inviteOpt = adminInviteService.findValidPendingInvite(token);

        if (inviteOpt.isEmpty()) {
            sender.sendText(chatId, "Havola yaroqsiz yoki muddati tugagan.");
            return;
        }

        AdminInvite invite = inviteOpt.get();
        adminInviteService.markAwaitingConfirmation(invite, userId, firstName);

        sender.sendText(chatId, "So'rovingiz yuborildi. Admin tasdiqlashini kuting.");

        String notifyText = "👤 " + firstName + " (id: " + userId + ") sizning havolangiz orqali admin bo'lishni so'ramoqda.\n\nTasdiqlaysizmi?";
        sender.sendText(invite.getCreatedByUserId(), notifyText, Keyboards.inviteConfirmMenu(invite.getId()));
    }

    // INVITEOK: / INVITENO: — havola egasi tasdiqlaydi yoki rad etadi (admin tekshiruvidan oldin chaqiriladi)
    public boolean handleInviteCallback(long chatId, long userId, String data) {
        if (data.startsWith("INVITEOK:")) {
            handleInviteConfirm(chatId, userId, Long.parseLong(data.substring("INVITEOK:".length())));
            return true;
        }
        if (data.startsWith("INVITENO:")) {
            handleInviteReject(chatId, userId, Long.parseLong(data.substring("INVITENO:".length())));
            return true;
        }
        return false;
    }

    private void handleInviteConfirm(long chatId, long userId, Long inviteId) {
        Optional<AdminInvite> inviteOpt = adminInviteService.findById(inviteId);

        if (inviteOpt.isEmpty()) {
            sender.sendText(chatId, "So'rov topilmadi.");
            return;
        }

        AdminInvite invite = inviteOpt.get();

        if (!invite.getCreatedByUserId().equals(userId)) {
            sender.sendText(chatId, "Bu so'rov sizga tegishli emas.");
            return;
        }

        if (invite.getStatus() != InviteStatus.AWAITING_CONFIRMATION) {
            sender.sendText(chatId, "Bu so'rov allaqachon hal qilingan.");
            return;
        }

        userService.makeAdmin(invite.getTargetUserId());
        adminInviteService.confirm(invite);

        sender.sendText(chatId, "✅ " + invite.getTargetName() + " admin qilindi.");
        showMenu(chatId);
        sender.sendText(invite.getTargetUserId(), "🎉 Tabriklaymiz! Siz endi administrator. /start bosing.");
    }

    private void handleInviteReject(long chatId, long userId, Long inviteId) {
        Optional<AdminInvite> inviteOpt = adminInviteService.findById(inviteId);

        if (inviteOpt.isEmpty()) {
            sender.sendText(chatId, "So'rov topilmadi.");
            return;
        }

        AdminInvite invite = inviteOpt.get();

        if (!invite.getCreatedByUserId().equals(userId)) {
            sender.sendText(chatId, "Bu so'rov sizga tegishli emas.");
            return;
        }

        if (invite.getStatus() != InviteStatus.AWAITING_CONFIRMATION) {
            sender.sendText(chatId, "Bu so'rov allaqachon hal qilingan.");
            return;
        }

        adminInviteService.reject(invite);

        sender.sendText(chatId, "❌ Rad etildi.");
        sender.sendText(invite.getTargetUserId(), "So'rovingiz admin tomonidan rad etildi.");
    }
}
