package com.moviebot.bot.enums;

public enum BroadcastStatus {
    SCHEDULED,
    SENDING,
    DONE,
    CANCELLED,
    // yuborish paytida bot qayta ishga tushdi; qolganlarga qayta yuborilmaydi (takror bo'lmasin)
    INTERRUPTED
}
