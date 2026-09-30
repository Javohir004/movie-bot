package com.moviebot.bot.service;

import com.moviebot.bot.domain.AdminInvite;
import com.moviebot.bot.enums.InviteStatus;
import com.moviebot.bot.repo.AdminInviteRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminInviteService {

    private static final int EXPIRY_MINUTES = 10;

    private final AdminInviteRepository adminInviteRepository;

    public AdminInviteService(AdminInviteRepository adminInviteRepository) {
        this.adminInviteRepository = adminInviteRepository;
    }

    public AdminInvite createInvite(Long createdByUserId) {
        AdminInvite invite = AdminInvite.builder()
                .token(UUID.randomUUID().toString().replace("-", ""))
                .createdByUserId(createdByUserId)
                .status(InviteStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES))
                .build();

        return adminInviteRepository.save(invite);
    }

    public Optional<AdminInvite> findValidPendingInvite(String token) {
        return adminInviteRepository.findByToken(token)
                .filter(invite -> invite.getStatus() == InviteStatus.PENDING)
                .filter(invite -> invite.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    public Optional<AdminInvite> findById(Long id) {
        return adminInviteRepository.findById(id);
    }

    public void markAwaitingConfirmation(AdminInvite invite, Long targetUserId, String targetName) {
        invite.setTargetUserId(targetUserId);
        invite.setTargetName(targetName);
        invite.setStatus(InviteStatus.AWAITING_CONFIRMATION);
        adminInviteRepository.save(invite);
    }

    public void confirm(AdminInvite invite) {
        invite.setStatus(InviteStatus.CONFIRMED);
        adminInviteRepository.save(invite);
    }

    public void reject(AdminInvite invite) {
        invite.setStatus(InviteStatus.REJECTED);
        adminInviteRepository.save(invite);
    }
}