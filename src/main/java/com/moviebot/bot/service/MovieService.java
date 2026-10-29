package com.moviebot.bot.service;

import com.moviebot.bot.domain.Movie;
import com.moviebot.bot.enums.MovieType;
import com.moviebot.bot.repo.MovieRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
public class MovieService {

    // Qidiruvda bu belgilar oddiy ' ga keltiriladi (o'zbekcha oʻ, o‘, o’, o` hammasi bir xil).
    // SQL translate() uchun ikkala qator uzunligi teng bo'lishi shart.
    static final String APOSTROPHES = "ʻʼ‘’`´";
    static final String PLAIN = "''''''";
    public static final int MAX_RESULTS = 30;
    private static final int MAX_SUGGESTIONS = 5;
    private static final double SIMILARITY_THRESHOLD = 0.3;

    private final MovieRepository movieRepository;

    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    public Optional<Movie> findByCode(String code) {
        return movieRepository.findByCodeAndActiveTrue(code.trim());
    }

    // avval aynan mos kod, bo'lmasa katta-kichik harfni farqlamasdan
    public Optional<Movie> findByCodeAnyCase(String code) {
        Optional<Movie> exact = findByCode(code);
        return exact.isPresent() ? exact : movieRepository.findFirstByCodeIgnoreCaseAndActiveTrueOrderByIdAsc(code.trim());
    }

    public record SearchResult(List<Movie> matches, List<Movie> suggestions) {
    }

    public static String normalize(String text) {
        String result = text.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
        for (int i = 0; i < APOSTROPHES.length(); i++) {
            result = result.replace(APOSTROPHES.charAt(i), '\'');
        }
        return result;
    }

    // kod -> nom bo'yicha -> topilmasa o'xshash nomlar
    public SearchResult search(String query) {
        Optional<Movie> byCode = findByCodeAnyCase(query);
        if (byCode.isPresent()) {
            return new SearchResult(List.of(byCode.get()), List.of());
        }

        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            return new SearchResult(List.of(), List.of());
        }

        List<Movie> matches = movieRepository.searchByNormalizedTitle(normalized, APOSTROPHES, PLAIN, MAX_RESULTS);
        if (!matches.isEmpty()) {
            return new SearchResult(matches, List.of());
        }
        return new SearchResult(List.of(), findSimilar(normalized));
    }

    private List<Movie> findSimilar(String normalized) {
        if (normalized.length() < 3) return List.of();

        try {
            return movieRepository.findSimilarTitles(normalized, APOSTROPHES, PLAIN, SIMILARITY_THRESHOLD, MAX_SUGGESTIONS);
        } catch (DataAccessException e) {
            // pg_trgm o'rnatilmagan bazada takliflar shunchaki chiqmaydi
            log.warn("O'xshash nomlarni qidirib bo'lmadi (pg_trgm yo'qmi?): {}", e.getMessage());
            return List.of();
        }
    }

    public List<Movie> findAll() {
        return movieRepository.findAllByActiveTrue();
    }

    public List<Movie> findByGenre(Long genreId) {
        return movieRepository.findActiveByGenre(genreId);
    }

    public Movie getById(Long id) {
        return movieRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Movie topilmadi: id=" + id));
    }

    public Movie save(String title, String code, MovieType type, String description, String fileId, String posterFileId) {
        Movie movie = Movie.builder()
                .title(title)
                .code(code)
                .type(type)
                .description(description)
                .fileId(fileId)
                .posterFileId(posterFileId)
                .active(true)
                .build();

        return movieRepository.save(movie);
    }

    public Movie saveWithoutVideo(String title, String code, MovieType type, String description) {
        Movie movie = Movie.builder()
                .title(title)
                .code(code)
                .type(type)
                .description(description)
                .active(true)
                .build();

        return movieRepository.save(movie);
    }

    public Movie update(String originalCode, String title, String code, MovieType type, String description,
                       String fileId, String posterFileId) {
        Movie movie = movieRepository.findByCodeAndActiveTrue(originalCode)
                .orElseThrow(() -> new IllegalStateException("Movie topilmadi: " + originalCode));

        movie.setTitle(title);
        movie.setCode(code);
        movie.setType(type);
        movie.setDescription(description);

        if (fileId != null) {
            movie.setFileId(fileId);
        }
        if (posterFileId != null) {
            movie.setPosterFileId(posterFileId);
        }

        return movieRepository.save(movie);
    }

    public void incrementViewCount(Movie movie) {
        movie.setViewCount(movie.getViewCount() + 1);
        movieRepository.save(movie);
    }

    public boolean isCodeTaken(String code) {
        // a1 va A1 ikki xil kino bo'lib qolmasin (qidiruv katta-kichik harfni farqlamaydi)
        return movieRepository.existsByCodeIgnoreCase(code.trim());
    }

    public void deleteByCode(String code) {
        movieRepository.findByCodeAndActiveTrue(code).ifPresent(movie -> {
            movie.setActive(false);
            // kod unique, shuning uchun o'chirilgan kinoning kodini bo'shatamiz
            movie.setCode(movie.getCode() + "#deleted-" + movie.getId());
            movieRepository.save(movie);
        });
    }

    public void updatePoster(Movie movie, String posterFileId) {
        movie.setPosterFileId(posterFileId);
        movieRepository.save(movie);
    }
}