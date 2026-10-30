# Changelog

All notable changes to this project are documented here.
Versions follow [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`.

## [1.0.0] — 2026-10-30

First release ready for production and commercial licensing.

### For users
- Interface in three languages: Uzbek, Russian and English (chosen on first `/start`, changeable from the menu).
- Search by code (case-insensitive) or title; Uzbek apostrophes (`o'`, `oʻ`, `o‘`, `o’`) are treated as the same letter.
- "Did you mean…" suggestions for misspelled titles.
- Browse the full catalog and genres with paginated lists; Top 5 most watched.
- Series and anime: seasons, episode grid, "Next episode" button.
- Genre names shown in the user's language.

### For admins
- Add, edit and delete movies, series and anime from Telegram; seasons, posters and single episodes.
- Statistics: total, reachable and blocked users, new and active users for today / 7 / 30 days, content and views.
- Sponsor (required) channels:
  - public channels by `@username`, private channels by ID with an automatic join-request link;
  - a duration per channel (1–30 days, any number of days, or unlimited); expired channels are removed automatically and admins get the final result;
  - count of unique users who joined through the bot.
- Ads (broadcasts):
  - any message type (text, photo, video, GIF, file), optional link button (`@channel`, `t.me/…` or `https://…`);
  - preview before sending, send now or at a chosen time, cancel scheduled ads, history with results;
  - sent in the background at a safe rate; users who blocked the bot are excluded from future ads.
- Genres with translations, tagged from the movie card.
- Invite-based admin onboarding with confirmation; the root admin is protected.

### Reliability
- Unfinished admin flows are saved and survive a restart.
- An ad interrupted by a restart is never sent twice.
- Long descriptions are shortened to fit Telegram's caption limit; lists are paginated to fit button limits.
- Cancelling a series keeps the seasons that were already uploaded.

### Setup and operations
- Docker Compose: bot, PostgreSQL and daily backups with one command.
- All settings from `.env` / environment variables; no code changes needed.
- Database schema managed by Flyway migrations; existing databases are upgraded automatically.
- Uzbek server installation guide (`docs/INSTALL_UZ.md`) and admin guide (`docs/ADMIN_GUIDE_UZ.md`).

### Development
- Code split into focused handlers; all Telegram calls go through one sender.
- 99 automated tests, including end-to-end bot behavior tests with fake Telegram updates.
- GitHub Actions CI runs the tests and the Docker build on every pull request.

### Known limitations
- Telegram `file_id`s belong to one bot: content uploaded to one bot can't be moved to another bot, so every new installation starts with an empty catalog.
- The admin panel is in Uzbek only. Movie titles and descriptions are stored in one language.
- A single season with more than ~95 episodes won't fit in one keyboard; split very long seasons into parts.
