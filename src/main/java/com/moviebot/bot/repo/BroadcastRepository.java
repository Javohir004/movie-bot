package com.moviebot.bot.repo;

import com.moviebot.bot.domain.Broadcast;
import com.moviebot.bot.enums.BroadcastStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface BroadcastRepository extends JpaRepository<Broadcast, Long> {

    List<Broadcast> findByStatusAndSendAtLessThanEqualOrderBySendAtAsc(BroadcastStatus status, LocalDateTime now, Pageable pageable);

    List<Broadcast> findByStatusOrderBySendAtAsc(BroadcastStatus status);

    List<Broadcast> findByStatusInOrderByIdDesc(Collection<BroadcastStatus> statuses, Pageable pageable);

    // holat faqat kutilgan holatda bo'lsa o'zgaradi: bekor qilish va yuborish bir vaqtga to'g'ri kelsa ham to'qnashmaydi
    @Modifying
    @Query("update Broadcast b set b.status = :toStatus, b.startedAt = :startedAt, b.total = :total where b.id = :id and b.status = :fromStatus")
    int startIfStatus(@Param("id") Long id, @Param("fromStatus") BroadcastStatus from, @Param("toStatus") BroadcastStatus to,
                      @Param("startedAt") LocalDateTime startedAt, @Param("total") int total);

    @Modifying
    @Query("update Broadcast b set b.status = :toStatus where b.id = :id and b.status = :fromStatus")
    int changeStatusIf(@Param("id") Long id, @Param("fromStatus") BroadcastStatus from, @Param("toStatus") BroadcastStatus to);

    @Modifying
    @Query("update Broadcast b set b.sent = :sent, b.blocked = :blocked, b.failed = :failed where b.id = :id")
    void saveProgress(@Param("id") Long id, @Param("sent") int sent, @Param("blocked") int blocked, @Param("failed") int failed);

    @Modifying
    @Query("update Broadcast b set b.status = :status, b.sent = :sent, b.blocked = :blocked, b.failed = :failed, b.finishedAt = :finishedAt where b.id = :id")
    void finish(@Param("id") Long id, @Param("status") BroadcastStatus status, @Param("sent") int sent,
                @Param("blocked") int blocked, @Param("failed") int failed, @Param("finishedAt") LocalDateTime finishedAt);
}
