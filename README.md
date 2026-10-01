# 🎬 Movie Bot

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

The project follows a simple layered architecture:

```
Telegram Update
      │
      ▼
   MovieBot            → handles Telegram updates, routes commands & callbacks
      │
      ▼
   Services            → MovieService, SeasonService, EpisodeService, UserService, AdminInviteService
      │
      ▼
   Repositories         → Spring Data JPA interfaces
      │
      ▼
   PostgreSQL
```

Admin conversations (adding/editing a movie, adding seasons & episodes) are managed through a lightweight **in-memory session state machine** (`AdminSessionManager`), so multiple admins can work independently at the same time.

## 📂 Project Structure

```
src/main/java/com/moviebot/bot/
├── MovieBotApplication.java
├── BotConfig.java
├── bot/
│   ├── MovieBot.java
│   └── AdminSessionManager.java
├── domain/
│   ├── Movie.java
│   ├── Season.java
│   ├── Episode.java
│   ├── User.java
│   ├── AdminInvite.java
│   ├── PendingMovie.java
│   └── Keyboards.java
├── enums/
│   ├── MovieType.java
│   ├── Role.java
│   ├── AdminState.java
│   └── InviteStatus.java
├── repo/
│   ├── MovieRepository.java
│   ├── SeasonRepository.java
│   ├── EpisodeRepository.java
│   ├── UserRepository.java
│   └── AdminInviteRepository.java
└── service/
    ├── MovieService.java
    ├── SeasonService.java
    ├── EpisodeService.java
    ├── UserService.java
    └── AdminInviteService.java
```

## 🗄 Database Schema (simplified)

| Table           | Description                                                                 |
|-----------------|-------------------------------------------------------------------------------|
| `movies`        | Title, code, type, description, video `file_id`, poster `file_id`, view count, `is_active` |
| `seasons`       | Linked to a movie, has a season number, its own poster `file_id`, view count   |
| `episodes`      | Linked to a season, has an episode number + `file_id`                          |
| `users`         | Telegram user ID, first name, role (`USER` / `ADMIN`)                          |
| `admin_invites` | One-time admin invite tokens: creator, target (once claimed), status, expiry   |

Deletions are **soft deletes** — an `is_active` flag is toggled instead of removing rows, keeping the data recoverable.

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Maven
- PostgreSQL
- A Telegram bot token (create one via [@BotFather](https://t.me/BotFather))

### 1. Clone the repository

```bash
git clone https://github.com/your-username/movie-bot.git
cd movie-bot
```

### 2. Create the database

```sql
CREATE DATABASE moviebot;
```

### 3. Configure the application

Copy the example config and fill in your own values:

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

```properties
bot.token=YOUR_BOT_TOKEN
bot.username=your_bot_username
bot.root-admin-id=YOUR_TELEGRAM_USER_ID

spring.datasource.url=jdbc:postgresql://localhost:5432/moviebot
spring.datasource.username=postgres
spring.datasource.password=YOUR_DB_PASSWORD
```

> `application.properties` is git-ignored — never commit real credentials.

Your Telegram user ID can be obtained from bots like **@userinfobot**.

### 4. Run the application

```bash
mvn spring-boot:run
```

The first time it starts, the account set in `bot.root-admin-id` is automatically registered as an admin.

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
- Pagination for large catalogs
- Migration tooling (Flyway/Liquibase) instead of `ddl-auto: update`
- Persisted admin sessions (currently in-memory, reset on restart)
- Structured logging instead of `printStackTrace`

## 📄 License

This project was built as a personal/learning project. Feel free to fork and adapt it.
