package com.moviebot.bot.service;

import com.moviebot.bot.enums.MovieType;
import com.moviebot.bot.enums.Role;
import com.moviebot.bot.repo.MovieRepository;
import com.moviebot.bot.repo.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class StatsService {

    private final UserRepository userRepository;
    private final MovieRepository movieRepository;

    public StatsService(UserRepository userRepository, MovieRepository movieRepository) {
        this.userRepository = userRepository;
        this.movieRepository = movieRepository;
    }

    public record Stats(
            long totalUsers,
            long reachableUsers,
            long blockedUsers,
            long newToday,
            long newWeek,
            long newMonth,
            long activeToday,
            long activeWeek,
            long activeMonth,
            long admins,
            long movies,
            long series,
            long anime,
            long totalViews
    ) {
    }

    public Stats collect() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        LocalDateTime week = today.minusDays(6);
        LocalDateTime month = today.minusDays(29);

        long total = userRepository.count();
        long reachable = userRepository.countByActiveTrue();

        return new Stats(
                total,
                reachable,
                total - reachable,
                userRepository.countByCreatedAtGreaterThanEqual(today),
                userRepository.countByCreatedAtGreaterThanEqual(week),
                userRepository.countByCreatedAtGreaterThanEqual(month),
                userRepository.countByLastSeenAtGreaterThanEqual(today),
                userRepository.countByLastSeenAtGreaterThanEqual(week),
                userRepository.countByLastSeenAtGreaterThanEqual(month),
                userRepository.countByRole(Role.ADMIN),
                movieRepository.countByTypeAndActiveTrue(MovieType.MOVIE),
                movieRepository.countByTypeAndActiveTrue(MovieType.SERIES),
                movieRepository.countByTypeAndActiveTrue(MovieType.ANIME),
                movieRepository.sumMovieViews() + movieRepository.sumSeasonViews()
        );
    }

    public String buildReport() {
        Stats s = collect();
        return "📊 <b>Statistika</b>\n\n"
                + "👥 <b>Foydalanuvchilar</b>\n"
                + "Jami: " + s.totalUsers() + "\n"
                + "Xabar yetib boradi: " + s.reachableUsers() + "\n"
                + "Botni bloklagan: " + s.blockedUsers() + "\n\n"
                + "🆕 <b>Yangi qo'shilganlar</b>\n"
                + "Bugun: " + s.newToday() + "\n"
                + "7 kun: " + s.newWeek() + "\n"
                + "30 kun: " + s.newMonth() + "\n\n"
                + "🔥 <b>Faol foydalanuvchilar</b>\n"
                + "Bugun: " + s.activeToday() + "\n"
                + "7 kun: " + s.activeWeek() + "\n"
                + "30 kun: " + s.activeMonth() + "\n\n"
                + "🎬 <b>Kontent</b>\n"
                + "Kino: " + s.movies() + "\n"
                + "Serial: " + s.series() + "\n"
                + "Anime: " + s.anime() + "\n"
                + "Jami ko'rishlar: " + s.totalViews() + "\n\n"
                + "👤 Adminlar: " + s.admins() + "\n\n"
                + "<i>Yangi va faol foydalanuvchilar statistika qo'shilgan kundan boshlab hisoblanadi.</i>";
    }
}
