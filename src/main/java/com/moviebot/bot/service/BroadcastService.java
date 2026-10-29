package com.moviebot.bot.service;

import com.moviebot.bot.domain.Broadcast;
import com.moviebot.bot.enums.BroadcastStatus;
import com.moviebot.bot.repo.BroadcastRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Service
public class BroadcastService {

    private final BroadcastRepository repository;

    public BroadcastService(BroadcastRepository repository) {
        this.repository = repository;
    }

    public Broadcast schedule(Long createdBy, Long sourceChatId, Integer sourceMessageId,
                              String buttonText, String buttonUrl, LocalDateTime sendAt) {
        return repository.save(Broadcast.builder()
                .createdBy(createdBy)
                .sourceChatId(sourceChatId)
                .sourceMessageId(sourceMessageId)
                .buttonText(buttonText)
                .buttonUrl(buttonUrl)
                .sendAt(sendAt)
                .status(BroadcastStatus.SCHEDULED)
                .createdAt(LocalDateTime.now())
                .build());
    }

    public Optional<Broadcast> findNextDue() {
        return repository.findByStatusAndSendAtLessThanEqualOrderBySendAtAsc(
                BroadcastStatus.SCHEDULED, LocalDateTime.now(), PageRequest.of(0, 1)).stream().findFirst();
    }

    public List<Broadcast> findScheduled() {
        return repository.findByStatusOrderBySendAtAsc(BroadcastStatus.SCHEDULED);
    }

    public List<Broadcast> findSending() {
        return repository.findByStatusOrderBySendAtAsc(BroadcastStatus.SENDING);
    }

    public List<Broadcast> findRecentFinished(int limit) {
        return repository.findByStatusInOrderByIdDesc(
                EnumSet.of(BroadcastStatus.DONE, BroadcastStatus.INTERRUPTED, BroadcastStatus.SENDING),
                PageRequest.of(0, limit));
    }

    // false qaytarsa, reklamani boshqa joyda allaqachon bekor qilishgan yoki boshlangan
    @Transactional
    public boolean markSending(Long id, int total) {
        return repository.startIfStatus(id, BroadcastStatus.SCHEDULED, BroadcastStatus.SENDING, LocalDateTime.now(), total) == 1;
    }

    // faqat hali boshlanmagan reklama bekor qilinadi
    @Transactional
    public boolean cancel(Long id) {
        return repository.changeStatusIf(id, BroadcastStatus.SCHEDULED, BroadcastStatus.CANCELLED) == 1;
    }

    @Transactional
    public boolean markInterrupted(Long id) {
        return repository.changeStatusIf(id, BroadcastStatus.SENDING, BroadcastStatus.INTERRUPTED) == 1;
    }

    @Transactional
    public void saveProgress(Long id, int sent, int blocked, int failed) {
        repository.saveProgress(id, sent, blocked, failed);
    }

    @Transactional
    public void finish(Long id, int sent, int blocked, int failed) {
        repository.finish(id, BroadcastStatus.DONE, sent, blocked, failed, LocalDateTime.now());
    }
}
