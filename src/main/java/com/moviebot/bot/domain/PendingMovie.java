package com.moviebot.bot.domain;

import com.moviebot.bot.enums.MovieType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PendingMovie {

    private String title;
    private String code;
    private MovieType type;
    private String description;
    private String editingCode;

    private String posterFileId;
    private String seasonPosterFileId;

    private Long movieId;
    private Integer seasonCount;
    private Integer currentSeasonNumber;
    private Long currentSeasonId;
    private Integer episodeCountForCurrentSeason;
    private Integer currentEpisodeNumber;

    private Integer seasonsProcessed;
    private boolean seasonAdditionOnly;

    private Long broadcastSourceChatId;
    private Integer broadcastSourceMessageId;
    private String broadcastButtonText;
    private String broadcastButtonUrl;

    // homiy kanal qo'shish jarayoni
    private String channelUsername;
    private String channelChatId;
    private String channelTitle;
    private String channelInviteLink;
    private boolean channelJoinRequest;

    public boolean isEditing() { return editingCode != null; }
}