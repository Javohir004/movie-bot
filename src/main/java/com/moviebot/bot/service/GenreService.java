package com.moviebot.bot.service;

import com.moviebot.bot.domain.Genre;
import com.moviebot.bot.repo.GenreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class GenreService {

    public static final int MAX_NAME_LENGTH = 40;

    private final GenreRepository genreRepository;

    public GenreService(GenreRepository genreRepository) {
        this.genreRepository = genreRepository;
    }

    public record AddResult(List<String> added, List<String> skipped) {
    }

    // "Komediya, Drama, Jangari" kabi bir nechtasini bir yo'la qo'shadi; borlari va noto'g'rilari o'tkazib yuboriladi
    public AddResult addAll(String input) {
        List<String> added = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (String raw : input.split("[,\\n]")) {
            String name = raw.trim().replaceAll("\\s+", " ");
            if (name.isEmpty()) continue;

            if (name.length() > MAX_NAME_LENGTH || genreRepository.existsByNameIgnoreCase(name)
                    || added.stream().anyMatch(name::equalsIgnoreCase)) {
                skipped.add(name);
                continue;
            }

            genreRepository.save(Genre.builder().name(name).createdAt(LocalDateTime.now()).build());
            added.add(name);
        }
        return new AddResult(added, skipped);
    }

    public List<Genre> findAll() {
        return genreRepository.findAllByOrderByNameAsc();
    }

    public List<GenreRepository.GenreWithCount> findAllWithMovieCount() {
        return genreRepository.findAllWithMovieCount();
    }

    // foydalanuvchiga faqat ichida kino bor janrlar ko'rsatiladi
    public List<GenreRepository.GenreWithCount> findNonEmpty() {
        return genreRepository.findAllWithMovieCount().stream().filter(g -> g.getMovies() > 0).toList();
    }

    public Optional<Genre> findById(Long id) {
        return genreRepository.findById(id);
    }

    public void remove(Long id) {
        genreRepository.deleteById(id);
    }

    public List<Long> genreIdsForMovie(Long movieId) {
        return genreRepository.findGenreIdsByMovie(movieId);
    }

    public List<String> namesForMovie(Long movieId) {
        return genreRepository.findNamesByMovie(movieId);
    }

    // belgilangan bo'lsa olib tashlaydi, bo'lmasa qo'shadi; qaytadi: endi belgilanganmi
    @Transactional
    public boolean toggle(Long movieId, Long genreId) {
        if (genreRepository.unlink(movieId, genreId) > 0) {
            return false;
        }
        genreRepository.link(movieId, genreId);
        return true;
    }
}
