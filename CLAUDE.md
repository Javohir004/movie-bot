# Movie Bot

Telegram bot for movies, series and anime.

Stack: Java, Spring Boot, PostgreSQL, Lombok, TelegramBots (long polling), Maven.

## Package layout (`src/main/java/com/moviebot/bot`)

- `bot/` — MovieBot (entry point), AdminSessionManager, BroadcastDispatcher, ChannelExpiryJob, I18n, Keyboards, TelegramSender, TextFormat
- `bot/handler/` — UserHandler, Admin*Handler (Movie, Genre, Channel, Broadcast, Panel), SubscriptionGate, BotTexts
- `domain/` — JPA entities (Lombok, no manual getters/setters)
- `enums/` — AdminState, BroadcastStatus, InviteStatus, MovieType, Role
- `repo/` — Spring Data repositories, one per entity
- `service/` — business logic, one service per area (Movie, Season, Episode, Genre, User, Broadcast, RequiredChannel, AdminInvite, Stats)
- `config/BotConfig.java`

## Tests (`src/test/java`)

- Service tests in `service/`, bot tests in `bot/`
- `BotHarness.java` is the shared helper for bot behaviour tests

## Rules

- Handlers call services, never repositories directly
- Soft delete only (`active=false`), never hard delete movies
- Check `I18n.java` and `BotTexts.java` before adding any user-facing text
- Never read or edit `application.properties` (secrets, git-ignored). Use `application.properties.example`
- Do not touch `target/`

## Commands

- Build: `mvn clean package`
- Test: `mvn test`
- Run one test: `mvn -Dtest=MovieServiceTest test`
