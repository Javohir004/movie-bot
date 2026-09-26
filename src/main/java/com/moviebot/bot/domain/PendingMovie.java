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

    public boolean isEditing() { return editingCode != null; }

}
