# 🎬 Movie Bot

A Telegram bot built with **Spring Boot** that lets users search for movies, TV series, and anime by title or code, then instantly receive the video. Admins can manage the entire catalog directly from Telegram — no external panel needed.

## ✨ Features

### For users
- 🔍 Search movies/series/anime by **title** or **code**
- 📽 Browse the full catalog with one tap
- 🎞 Get videos delivered directly in the chat (via Telegram's `file_id`, no re-uploading)
- 📺 For series/anime: navigate through **seasons** and **episodes**

### For admins
- ➕ Add new movies, series, or anime through a guided, step-by-step flow
- ✏️ Edit existing entries (title, code, type, description, video)
- 🗑 Soft-delete entries (hidden from users, but recoverable — no data is permanently lost)
- 📋 View the full catalog with inline management options
- 👤 Promote other Telegram users to admin
- 🔐 Role-based access — regular users never see admin functionality

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
   Services            → MovieService, SeasonService, EpisodeService, UserService
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
├── AdminSessionManager.java
├── domain/
│   ├── Movie.java
│   ├── Season.java
│   ├── Episode.java
│   ├── User.java
│   ├── PendingMovie.java
│   └── Keyboards.java
├── enums/
│   ├── MovieType.java
│   ├── Role.java
│   └── AdminState.java
├── repo/
│   ├── MovieRepository.java
│   ├── SeasonRepository.java
│   ├── EpisodeRepository.java
│   └── UserRepository.java
└── service/
    ├── MovieService.java
    ├── SeasonService.java
    ├── EpisodeService.java
    └── UserService.java
```

## 🗄 Database Schema (simplified)

| Table      | Description                                      |
|------------|---------------------------------------------------|
| `movies`   | Title, code, type, description, video `file_id`, `is_active` |
| `seasons`  | Linked to a movie, has a season number             |
| `episodes` | Linked to a season, has an episode number + `file_id` |
| `users`    | Telegram user ID and role (`USER` / `ADMIN`)       |

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
2. Choose **🔍 Search** to look up a title/code, or **📽 Browse all** to see the full catalog
3. Tap a result to receive the video (or navigate seasons/episodes for series & anime)

### As an admin
1. Press **/start** — the admin panel appears automatically for recognized admin accounts
2. **🎬 Add movie/series** — walks you through title, code, type, description, and video (or season/episode structure for series & anime)
3. **📋 List** — view, edit, or delete existing entries
4. **👤 Add admin** — promote another Telegram user by ID

## 🔒 Security Notes

- The bot token and database credentials are never committed to the repository
- Admin-only actions are verified **server-side** on every request — not just hidden in the UI
- Deleted content is soft-deleted, never silently destroyed

## 📌 Possible Improvements

- Dedicated panel to view/restore/permanently delete soft-deleted entries
- Pagination for large catalogs
- Migration tooling (Flyway/Liquibase) instead of `ddl-auto: update`
- Persisted admin sessions (currently in-memory, reset on restart)

## 📄 License

This project was built as a personal/learning project. Feel free to fork and adapt it.
