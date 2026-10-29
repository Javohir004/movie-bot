package com.moviebot.bot.bot;

import com.moviebot.bot.bot.handler.*;
import com.moviebot.bot.domain.User;
import com.moviebot.bot.enums.Role;
import com.moviebot.bot.repo.AdminSessionRepository;
import com.moviebot.bot.repo.UserRepository;
import com.moviebot.bot.service.*;
import org.mockito.ArgumentMatchers;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.Serializable;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Botni Telegram'siz ishga tushiradi: update'lar beriladi, bot yuborgan hamma narsa sent ro'yxatiga yig'iladi.
// Refaktoringdan oldin va keyin bir xil natija berishi kerak — shuning uchun faqat tashqi xatti-harakat tekshiriladi.
class BotHarness {

    static final long ADMIN_ID = 1000L;
    static final long USER_ID = 2000L;

    final List<Object> sent = new ArrayList<>();
    // GetChatMember javobi: kanal -> status (berilmasa "member")
    final Map<String, String> memberStatus = new HashMap<>();

    final UserRepository userRepository = mock(UserRepository.class);
    final UserService userService = new UserService(userRepository);
    final AdminSessionManager sessionManager;
    final MovieService movieService = mock(MovieService.class);
    final SeasonService seasonService = mock(SeasonService.class);
    final EpisodeService episodeService = mock(EpisodeService.class);
    final AdminInviteService adminInviteService = mock(AdminInviteService.class);
    final RequiredChannelService channelService = mock(RequiredChannelService.class);
    final StatsService statsService = mock(StatsService.class);
    final BroadcastService broadcastService = mock(BroadcastService.class);
    final GenreService genreService = mock(GenreService.class);
    final MovieBot bot;

    private final Map<Long, User> users = new HashMap<>();
    private int nextMessageId = 1;

    BotHarness() throws Exception {
        AdminSessionRepository sessionRepository = mock(AdminSessionRepository.class);
        when(sessionRepository.findById(any())).thenReturn(Optional.empty());
        sessionManager = new AdminSessionManager(sessionRepository);

        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);

        // foydalanuvchilar "bazasi"
        when(userRepository.findByUserId(any())).thenAnswer(inv -> Optional.ofNullable(users.get((Long) inv.getArgument(0))));
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            users.put(u.getUserId(), u);
            return u;
        });
        when(userRepository.existsByUserIdAndRole(any(), any())).thenAnswer(inv ->
                Optional.ofNullable(users.get((Long) inv.getArgument(0))).map(u -> u.getRole() == inv.getArgument(1)).orElse(false));
        addUser(ADMIN_ID, Role.ADMIN, "uz");

        when(channelService.getActiveChannels()).thenReturn(List.of());
        when(genreService.namesForMovie(any(), any())).thenReturn(List.of());

        // hamma yuborish TelegramSender orqali: uning execute'lari ushlanadi, haqiqiy Telegram'ga hech narsa ketmaydi
        TelegramSender sender = spy(new TelegramSender("123:TEST"));
        doAnswer(inv -> record(inv.getArgument(0))).when(sender).execute(ArgumentMatchers.<BotApiMethod<Serializable>>any());
        doAnswer(inv -> record(inv.getArgument(0))).when(sender).execute(any(SendPhoto.class));
        doAnswer(inv -> record(inv.getArgument(0))).when(sender).execute(any(SendVideo.class));

        // Spring'dagi kabi yig'iladi (handler'lar va ularning bog'liqliklari)
        I18n i18n = new I18n(messages);
        BotTexts texts = new BotTexts(userService, i18n);
        SubscriptionGate gate = new SubscriptionGate(sender, channelService, userService, texts);
        UserHandler userHandler = new UserHandler(sender, texts, gate, userService, movieService, seasonService,
                episodeService, genreService, "test_bot", "");
        AdminPanelHandler panel = new AdminPanelHandler(sender, userService, adminInviteService, statsService,
                sessionManager, "test_bot", ADMIN_ID);
        AdminMovieHandler movieHandler = new AdminMovieHandler(sender, sessionManager, movieService, seasonService,
                episodeService, genreService, panel);
        AdminChannelHandler channelHandler = new AdminChannelHandler(sender, sessionManager, channelService);
        AdminBroadcastHandler broadcastHandler = new AdminBroadcastHandler(sender, sessionManager, broadcastService,
                userService, panel);
        AdminGenreHandler genreHandler = new AdminGenreHandler(sender, sessionManager, genreService, movieService);

        bot = new MovieBot("123:TEST", "test_bot", sender, userService, sessionManager, gate, userHandler, panel,
                movieHandler, channelHandler, broadcastHandler, genreHandler);
    }

    private Object record(Object method) {
        sent.add(method);
        if (method instanceof GetChatMember get) {
            ChatMember member = mock(ChatMember.class);
            when(member.getStatus()).thenReturn(memberStatus.getOrDefault(get.getChatId(), "member"));
            return member;
        }
        if (method instanceof org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat) {
            Chat chat = new Chat();
            chat.setId(-100123L);
            chat.setType("channel");
            chat.setTitle("Test Kanal");
            return chat;
        }
        if (method instanceof GetMe) {
            org.telegram.telegrambots.meta.api.objects.User me = new org.telegram.telegrambots.meta.api.objects.User();
            me.setId(999L);
            return me;
        }
        if (method instanceof SendMessage || method instanceof SendPhoto || method instanceof SendVideo) {
            Message message = new Message();
            message.setMessageId(nextMessageId++);
            return message;
        }
        return null;
    }

    void addUser(long id, Role role, String language) {
        users.put(id, User.builder().userId(id).role(role).firstName("U" + id).language(language).build());
    }

    // ---- update'lar ----

    void text(long userId, String text) {
        Message message = message(userId);
        message.setText(text);
        send(message);
    }

    void photo(long userId) {
        Message message = message(userId);
        PhotoSize size = new PhotoSize();
        size.setFileId("photo-file");
        message.setPhoto(List.of(size));
        send(message);
    }

    void callback(long userId, String data) {
        CallbackQuery query = new CallbackQuery();
        query.setId("cb-" + nextMessageId);
        query.setFrom(tgUser(userId));
        query.setData(data);
        query.setMessage(message(userId));

        Update update = new Update();
        update.setCallbackQuery(query);
        sent.clear();
        bot.onUpdateReceived(update);
    }

    private void send(Message message) {
        Update update = new Update();
        update.setMessage(message);
        sent.clear();
        bot.onUpdateReceived(update);
    }

    private Message message(long userId) {
        Chat chat = new Chat();
        chat.setId(userId);
        chat.setType("private");
        Message message = new Message();
        message.setMessageId(nextMessageId++);
        message.setChat(chat);
        message.setFrom(tgUser(userId));
        return message;
    }

    private static org.telegram.telegrambots.meta.api.objects.User tgUser(long id) {
        org.telegram.telegrambots.meta.api.objects.User user = new org.telegram.telegrambots.meta.api.objects.User();
        user.setId(id);
        user.setFirstName("U" + id);
        user.setIsBot(false);
        return user;
    }

    // ---- natijalarni o'qish ----

    <T> List<T> sentOf(Class<T> type) {
        return sent.stream().filter(type::isInstance).map(type::cast).toList();
    }

    // yuborilgan barcha matnlar (xabar matni va rasm/video izohlari)
    List<String> texts() {
        List<String> result = new ArrayList<>();
        for (Object m : sent) {
            if (m instanceof SendMessage s) result.add(s.getText());
            if (m instanceof SendPhoto p) result.add(p.getCaption());
            if (m instanceof SendVideo v) result.add(v.getCaption());
        }
        return result;
    }

    String lastText() {
        List<String> all = texts();
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    // yuborilgan barcha inline tugmalarning callback/url qiymatlari
    List<String> buttons() {
        List<String> result = new ArrayList<>();
        for (Object m : sent) {
            Object markup = null;
            if (m instanceof SendMessage s) markup = s.getReplyMarkup();
            if (m instanceof SendPhoto p) markup = p.getReplyMarkup();
            if (m instanceof SendVideo v) markup = v.getReplyMarkup();
            if (m instanceof org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup e) markup = e.getReplyMarkup();
            if (m instanceof org.telegram.telegrambots.meta.api.methods.CopyMessage c) markup = c.getReplyMarkup();
            if (markup instanceof InlineKeyboardMarkup inline) {
                for (List<InlineKeyboardButton> row : inline.getKeyboard()) {
                    for (InlineKeyboardButton b : row) {
                        result.add(b.getCallbackData() != null ? b.getCallbackData() : b.getUrl());
                    }
                }
            }
        }
        return result;
    }
}
