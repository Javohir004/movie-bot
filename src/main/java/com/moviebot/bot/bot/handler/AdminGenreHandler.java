package com.moviebot.bot.bot.handler;

import com.moviebot.bot.bot.AdminSessionManager;
import com.moviebot.bot.bot.I18n;
import com.moviebot.bot.bot.Keyboards;
import com.moviebot.bot.bot.TelegramSender;
import com.moviebot.bot.bot.TextFormat;
import com.moviebot.bot.domain.Genre;
import com.moviebot.bot.domain.Movie;
import com.moviebot.bot.domain.PendingMovie;
import com.moviebot.bot.enums.AdminState;
import com.moviebot.bot.service.GenreService;
import com.moviebot.bot.service.MovieService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

// Admin: janrlar ro'yxati, tarjimalari va kinolarga janr belgilash
@Component
public class AdminGenreHandler {

    private final TelegramSender sender;
    private final AdminSessionManager sessionManager;
    private final GenreService genreService;
    private final MovieService movieService;

    public AdminGenreHandler(TelegramSender sender, AdminSessionManager sessionManager,
                             GenreService genreService, MovieService movieService) {
        this.sender = sender;
        this.sessionManager = sessionManager;
        this.genreService = genreService;
        this.movieService = movieService;
    }

    public boolean handleCallback(long chatId, long userId, Integer messageId, String data) {
        if (data.equals("ADMIN:GENRES")) {
            sessionManager.clear(userId);
            sendGenreAdminList(chatId);
            return true;
        }

        if (data.equals("ADMIN:ADD_GENRE")) {
            sessionManager.clear(userId);
            sessionManager.setState(userId, AdminState.AWAITING_GENRE_NAME);
            sender.sendText(chatId, "Janr nomini yozing. Bir nechtasini vergul bilan ajratib yozsangiz bo'ladi:\n"
                    + "Komediya, Drama, Jangari", Keyboards.backOnly());
            return true;
        }

        if (data.startsWith("GENREEDIT:")) {
            sessionManager.clear(userId);
            sendGenreEdit(chatId, Long.parseLong(data.substring("GENREEDIT:".length())));
            return true;
        }

        // GENRETR:<janrId>:<til> — shu tildagi nomni yozish so'raladi
        if (data.startsWith("GENRETR:")) {
            String[] parts = data.split(":");
            Long genreId = Long.parseLong(parts[1]);
            String language = I18n.normalize(parts[2]);

            sessionManager.clear(userId);
            PendingMovie pending = sessionManager.getPendingMovie(userId);
            pending.setGenreTranslationId(genreId);
            pending.setGenreTranslationLanguage(language);
            sessionManager.setState(userId, AdminState.AWAITING_GENRE_TRANSLATION);

            String languageName = switch (language) {
                case "ru" -> "ruscha";
                case "en" -> "inglizcha";
                default -> "o'zbekcha";
            };
            sender.sendText(chatId, "Janrning " + languageName + " nomini yozing.\n"
                    + "Tarjimani o'chirish uchun - yuboring (unda asosiy nom ko'rsatiladi).", Keyboards.backOnly());
            return true;
        }

        if (data.startsWith("RMGENRE:")) {
            Long genreId = Long.parseLong(data.substring("RMGENRE:".length()));
            genreService.findById(genreId).ifPresentOrElse(
                    genre -> sender.sendText(chatId, "\"" + genre.getName() + "\" janrini o'chirasizmi?\n"
                                    + "Kinolarning o'zi o'chmaydi, faqat ulardan bu janr olib tashlanadi.",
                            Keyboards.genreRemoveConfirmMenu(genreId)),
                    () -> sendGenreAdminList(chatId));
            return true;
        }

        if (data.startsWith("RMGENRECONF:")) {
            Long genreId = Long.parseLong(data.substring("RMGENRECONF:".length()));
            genreService.findById(genreId).ifPresent(genre -> {
                genreService.remove(genreId);
                sender.sendText(chatId, "✅ \"" + genre.getName() + "\" o'chirildi.");
            });
            sendGenreAdminList(chatId);
            return true;
        }

        if (data.startsWith("MGENRES:")) {
            sendMovieGenreToggle(chatId, Long.parseLong(data.substring("MGENRES:".length())), null);
            return true;
        }

        // MGT:<kinoId>:<janrId> — belgilash/olib tashlash, keyin o'sha xabarning tugmalari yangilanadi
        if (data.startsWith("MGT:")) {
            String[] parts = data.split(":");
            Long movieId = Long.parseLong(parts[1]);
            genreService.toggle(movieId, Long.parseLong(parts[2]));
            sendMovieGenreToggle(chatId, movieId, messageId);
            return true;
        }

        return false;
    }

    public boolean handleText(long chatId, long userId, AdminState state, String text) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);

        switch (state) {
            case AWAITING_GENRE_TRANSLATION -> {
                Long genreId = pending.getGenreTranslationId();
                String language = pending.getGenreTranslationLanguage();
                if (genreId == null || language == null) {
                    sessionManager.clear(userId);
                    sendGenreAdminList(chatId);
                    return true;
                }

                try {
                    if (genreService.setTranslation(genreId, language, text).isEmpty()) {
                        sessionManager.clear(userId);
                        sender.sendText(chatId, "Janr topilmadi.");
                        sendGenreAdminList(chatId);
                        return true;
                    }
                } catch (IllegalArgumentException e) {
                    sender.sendText(chatId, "Nom " + GenreService.MAX_NAME_LENGTH + " belgidan oshmasligi kerak. Qaytadan yozing:");
                    return true;
                }

                sessionManager.clear(userId);
                sender.sendText(chatId, "✅ Saqlandi.");
                sendGenreEdit(chatId, genreId);
            }
            case AWAITING_GENRE_NAME -> {
                GenreService.AddResult result = genreService.addAll(text);
                sessionManager.clear(userId);

                StringBuilder reply = new StringBuilder();
                if (!result.added().isEmpty()) {
                    reply.append("✅ Qo'shildi: ").append(String.join(", ", result.added()));
                }
                if (!result.skipped().isEmpty()) {
                    if (!reply.isEmpty()) reply.append("\n");
                    reply.append("⚠️ O'tkazib yuborildi (allaqachon bor yoki ").append(GenreService.MAX_NAME_LENGTH)
                            .append(" belgidan uzun): ").append(String.join(", ", result.skipped()));
                }
                sender.sendText(chatId, reply.isEmpty() ? "Hech qanday janr yozilmadi." : reply.toString());
                sendGenreAdminList(chatId);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void sendMovieGenreToggle(long chatId, Long movieId, Integer editMessageId) {
        Movie movie = movieService.getById(movieId);
        List<Genre> genres = genreService.findAll();

        if (genres.isEmpty()) {
            sender.sendText(chatId, "Hali janrlar qo'shilmagan. Avval admin paneldagi 🎭 Janrlar bo'limidan qo'shing.",
                    Keyboards.adminMenu());
            return;
        }

        InlineKeyboardMarkup markup = Keyboards.movieGenreToggleMenu(movieId, movie.getCode(), genres,
                new HashSet<>(genreService.genreIdsForMovie(movieId)));
        sender.sendOrEdit(chatId, editMessageId, "🎭 \"" + movie.getTitle() + "\" janrlari (bosib belgilang):", markup);
    }

    private void sendGenreEdit(long chatId, Long genreId) {
        Optional<Genre> genreOpt = genreService.findById(genreId);
        if (genreOpt.isEmpty()) {
            sendGenreAdminList(chatId);
            return;
        }

        Genre genre = genreOpt.get();
        String text = "🎭 <b>" + TextFormat.escape(genre.getName()) + "</b>\n\n"
                + "🇺🇿 O'zbekcha: " + translationOrDash(genre.getNameUz()) + "\n"
                + "🇷🇺 Русский: " + translationOrDash(genre.getNameRu()) + "\n"
                + "🇬🇧 English: " + translationOrDash(genre.getNameEn()) + "\n\n"
                + "<i>Tarjima yozilmagan tilda asosiy nom (" + TextFormat.escape(genre.getName()) + ") ko'rsatiladi.</i>";
        sender.sendHtml(chatId, text, Keyboards.genreEditMenu(genreId));
    }

    private String translationOrDash(String value) {
        return value == null || value.isBlank() ? "—" : TextFormat.escape(value);
    }

    private void sendGenreAdminList(long chatId) {
        var genres = genreService.findAllWithMovieCount();
        String text = genres.isEmpty()
                ? "🎭 Janrlar hali qo'shilmagan.\n\nQo'shilgan janrlar foydalanuvchi menyusida chiqadi, kinolarga esa kartochkasidagi 🎭 tugma orqali belgilanadi."
                : "🎭 Janrlar (qavs ichida kinolar soni).\n🌐 — uchala tilga tarjima qilingan, ⚠️ — tarjimasi chala.\n"
                + "Tarjima qilish yoki o'chirish uchun janrni bosing:";
        sender.sendText(chatId, text, Keyboards.genreAdminMenu(genres));
    }
}
