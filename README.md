# 🎬 Movie Bot

[![CI](https://github.com/Javohir004/movie-bot/actions/workflows/ci.yml/badge.svg)](https://github.com/Javohir004/movie-bot/actions/workflows/ci.yml)

A Telegram bot built with **Spring Boot** that lets users search for movies, TV series, and anime by title or code, then instantly receive the video. Admins can manage the entire catalog — including posters, seasons, and episodes — directly from Telegram, with no external panel needed.

## ✨ Features

### For users
- 🔍 Search movies/series/anime by **title** or **code**
- 📽 Browse the full catalog with one tap
- 🔥 **Top 5** most-viewed titles (counts episode views for series/anime, movie views for movies)
- 🖼 Rich result cards with **poster image**, description, view count, and code
- 🎞 Get videos delivered directly in the chat (via Telegram's `file_id`, no re-uploading)
- 📺 For series/anime: browse **seasons** (each with its own poster) and **episodes** (grid layout)
- ▶️ "Next episode" button after each video, shown only when a next episode actually exists

### For admins
- ➕ Add new movies, series, or anime through a guided, step-by-step flow (title → code → type → description → poster → video, or season/episode structure for series & anime)
- 🖼 Separate poster per season — prompted automatically when adding multiple seasons
- 📺 **Manage seasons** independently: replace a season's poster, or add a single new episode to an existing season (e.g. for weekly-airing anime) without recreating anything
- ✏️ Edit existing entries (title, code, type, description, video, poster)
- 🗑 Soft-delete entries (hidden from users, but recoverable — no data is permanently lost)
- 📋 View the full catalog with inline management options
- 👤 **Invite-based admin onboarding** — generate a one-time, expiring invite link instead of handling raw Telegram IDs; the invited user clicks it, and only the admin who created the link can approve or reject the request
- 👥 View all current admins and demote any of them back to a regular user (the root admin is protected and can't be removed)
- 🔐 Role-based access — regular users never see admin functionality, and every admin action is re-verified server-side, not just hidden in the UI

### Reliability
- Every update is processed inside a top-level try/catch, so an unexpected error affects only the single message, not the whole bot
- Null-safety checks guard against messages with no sender (e.g. anonymous group admins or channel posts)
- Stale callback buttons (season/episode already removed) fail gracefully with a message instead of crashing

## 🛠 Tech Stack

- **Java 17**
- **Spring Boot 3** (Spring Data JPA)
- **PostgreSQL**
- **TelegramBots** (long polling)
- **Lombok**
- **Hibernate**

## 🏗 Architecture

The project follows a layered architecture:

```
Telegram Update
      │
      ▼
   MovieBot            → receives updates and routes them (no business logic)
      │
      ├─ UserHandler           → language, menu, search, lists, genres, watching
      │    └─ SubscriptionGate → required-channel checks, join counting
      ├─ AdminPanelHandler     → admin menu, admins & invites, stats
      ├─ AdminMovieHandler     → add/edit movies & series, seasons, episodes
      ├─ AdminChannelHandler   → sponsor channels
      ├─ AdminBroadcastHandler → preparing ads (BroadcastDispatcher sends them)
      └─ AdminGenreHandler     → genres and their translations
      │
      ▼
   Services            → MovieService, UserService, BroadcastService, GenreService, ...
      │
      ▼
   Repositories        → Spring Data JPA interfaces
      │
      ▼
   PostgreSQL (schema managed by Flyway)
```

All outgoing Telegram calls go through `TelegramSender`, so handlers, the broadcast dispatcher and scheduled jobs never depend on `MovieBot` itself.

Callbacks are checked in a fixed order: user buttons (behind the subscription check) → admin invite confirmation → admin buttons. Admin callback prefixes don't overlap, so each admin handler only looks at its own.

Admin conversations (adding/editing a movie, adding seasons & episodes) are managed through a lightweight **session state machine** (`AdminSessionManager`), so multiple admins can work independently at the same time. Sessions are persisted to the `admin_sessions` table, so an unfinished flow survives a restart.

The database schema is managed by **Flyway** (`src/main/resources/db/migration`); Hibernate only validates it on startup.

## 📂 Project Structure

```
src/main/java/com/moviebot/bot/
├── MovieBotApplication.java
├── config/BotConfig.java          → registers the bot, ensures the root admin
├── bot/
│   ├── MovieBot.java              → update router
│   ├── TelegramSender.java        → all outgoing Telegram calls
│   ├── Keyboards.java             → inline keyboards
│   ├── I18n.java, TextFormat.java → translations, HTML escaping, caption limits
│   ├── AdminSessionManager.java   → admin flow state (persisted)
│   ├── BroadcastDispatcher.java   → sends scheduled ads in the background
│   ├── BroadcastInput.java        → parses ad buttons and send times
│   ├── ChannelExpiryJob.java      → removes expired sponsor channels
│   └── handler/                   → UserHandler, SubscriptionGate, Admin*Handler, BotTexts
├── domain/                        → JPA entities (+ PendingMovie, the admin flow draft)
├── enums/
├── repo/                          → Spring Data repositories
└── service/                       → business logic

src/main/resources/
├── db/migration/                  → Flyway migrations (V1…)
└── messages*.properties           → user-facing texts (uz / ru / en)

src/test/java/…
├── bot/MovieBotBehaviorTest.java  → end-to-end bot behavior with fake Telegram updates (BotHarness)
└── …                              → unit tests for services, keyboards, parsing, i18n
```

## 🗄 Database Schema (simplified)

| Table           | Description                                                                 |
|-----------------|-------------------------------------------------------------------------------|
| `movies`        | Title, code, type, description, video `file_id`, poster `file_id`, view count, `is_active` |
| `seasons`       | Linked to a movie, has a season number, its own poster `file_id`, view count   |
| `episodes`      | Linked to a season, has an episode number + `file_id`                          |
| `users`         | Telegram user ID, first name, role (`USER` / `ADMIN`)                          |
| `admin_invites` | One-time admin invite tokens: creator, target (once claimed), status, expiry   |
| `admin_sessions`| Unfinished admin flows (state + pending data as JSON), restored after restart  |
| `required_channels` | Channels users must join before using the bot                            |

Deletions are **soft deletes** — an `is_active` flag is toggled instead of removing rows, keeping the data recoverable.

## 🚀 Getting Started

> 🇺🇿 Uzbek guides: server installation — [docs/INSTALL_UZ.md](docs/INSTALL_UZ.md), using the admin panel — [docs/ADMIN_GUIDE_UZ.md](docs/ADMIN_GUIDE_UZ.md). Release notes: [CHANGELOG.md](CHANGELOG.md).

### Configuration

All settings come from environment variables. Copy the example file and fill it in:

```bash
cp .env.example .env
```

| Variable         | Required | Description                                              |
|------------------|----------|----------------------------------------------------------|
| `BOT_TOKEN`      | yes      | Token from [@BotFather](https://t.me/BotFather)           |
| `BOT_USERNAME`   | yes      | Bot username without `@`                                  |
| `ROOT_ADMIN_ID`  | yes      | Telegram ID of the main admin (get it from @userinfobot)  |
| `DB_PASSWORD`    | yes      | PostgreSQL password                                       |
| `BOT_GROUP_LINK` | no       | Group/channel shown under each title, e.g. `@mygroup`     |
| `DB_URL`, `DB_USERNAME` | no | Only for running without Docker (defaults: local `moviebot` DB, `postgres`) |
| `BACKUP_KEEP_DAYS` | no     | How many days of daily backups to keep (default `7`)      |

> `.env` is git-ignored — never commit real credentials. `application.properties` only contains `${...}` placeholders.

### Option A: Docker (recommended)

Requires Docker with Compose.

```bash
docker compose up -d --build
docker compose logs -f bot
```

This starts PostgreSQL, the bot, and a backup job that saves a compressed dump to `./backups` once a day.

### Option B: Run locally

Requires Java 17+ and a PostgreSQL database named `moviebot` (`CREATE DATABASE moviebot;`).

```bash
./mvnw spring-boot:run
```

Run it from the project root so the `.env` file is picked up. Environment variables override values from `.env`.

On first start Flyway creates the schema, and the account in `ROOT_ADMIN_ID` is registered as an admin. An existing database created before Flyway is detected automatically (baseline) and only newer migrations are applied.

### Running tests

```bash
./mvnw test
```

Unit tests need neither a database nor a bot token.

### Restoring a backup

Restore into an **empty** database, and start the bot only afterwards (otherwise Flyway creates the tables first and the restore conflicts with them):

```bash
docker compose down -v        # ⚠️ deletes the current database volume
docker compose up -d db
gunzip -c backups/moviebot-YYYYMMDD-HHMMSS.sql.gz | docker compose exec -T db psql -U postgres -d moviebot
docker compose up -d
```

## 🤖 Usage

### As a regular user
1. Start a chat with the bot and press **/start**
2. Choose **🔍 Search**, **📽 Browse all**, or **🔥 Top 5**
3. Tap a result to see its poster card, then watch the video — or for series/anime, pick a season and episode
4. After a video, use **▶️ Next episode** to keep watching without going back to the list

### As an admin
1. Press **/start** — the admin panel appears automatically for recognized admin accounts
2. **🎬 Add movie/series** — walks you through title, code, type, description, poster, and video (or season count + per-season poster + episode videos for series & anime)
3. **📋 List** — view, edit, or delete existing entries; for series/anime, jump into **📺 Manage seasons** to update a season poster or add a single new episode
4. **👤 Add admin** — generates a one-time invite link; send it to the person you want to promote. Once they open it, you get a confirmation prompt with ✅/❌ buttons
5. **👥 Admin list** — see every current admin and remove any of them except the root admin

## 🔒 Security Notes

- The bot token and database credentials are never committed to the repository
- Admin-only actions are verified **server-side** on every request — not just hidden in the UI
- Admin invite tokens are random (not derived from any Telegram ID), expire after 10 minutes, and are single-use — once claimed, the same link can't be reused
- Only the admin who generated an invite link can approve or reject it
- The root admin can't be demoted, and no admin can remove themselves
- Deleted content is soft-deleted, never silently destroyed

## 📌 Possible Improvements

- Dedicated panel to view/restore/permanently delete soft-deleted entries
- Favorites list, genre filtering, search history
- Multi-language interface (Uzbek / Russian / English)
- Admin statistics dashboard

## 📄 License

**Source-available, not open source.** You're welcome to read and study the code and run it
for personal, non-commercial evaluation. Commercial use — running it for a business or a bot
that earns money, selling, reselling, hosting it for others, or redistributing it — requires a
written commercial license. See [LICENSE](LICENSE).

> 🇺🇿 Kodni ko'rish va o'rganish mumkin. Botni pul topish uchun ishlatish, sotish yoki boshqalarga
> o'rnatib berish faqat muallifning yozma ruxsati (tijorat litsenziyasi) bilan.

For a commercial license (one license per bot), contact [@Feniks004](https://t.me/Feniks004) on Telegram
or [@Javohir004](https://github.com/Javohir004) on GitHub.

The bot ships without any media content. Whoever runs it is responsible for the content they
upload and for holding the rights to it.
