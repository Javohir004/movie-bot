# Botni serverga o'rnatish (bosqichma-bosqich)

Bu qo'llanma dasturchi bo'lmagan odam uchun yozilgan. Taxminan 20–30 daqiqa vaqt oladi.

## 1. Kerakli narsalar

- **VPS server** — Ubuntu 22.04 yoki 24.04, kamida 1 GB RAM (oyiga ~5$ dan boshlanadi).
- **Telegram bot tokeni** — [@BotFather](https://t.me/BotFather) ga `/newbot` yozing, nom bering, u sizga token beradi (`123456789:AA...` ko'rinishida).
- **O'zingizning Telegram ID raqamingiz** — [@userinfobot](https://t.me/userinfobot) ga `/start` yozing, `Id` qatoridagi raqam.

## 2. Serverga ulanish

Server sotib olganingizda IP manzil va parol beriladi. Kompyuteringizda terminal (Windows'da PowerShell) oching:

```bash
ssh root@SERVER_IP
```

## 3. Docker o'rnatish

Serverda quyidagini bajaring (bir marta):

```bash
curl -fsSL https://get.docker.com | sh
```

Tekshirish: `docker compose version` versiya raqamini chiqarishi kerak.

## 4. Botni yuklab olish

```bash
git clone https://github.com/Javohir004/movie-bot.git
cd movie-bot
```

## 5. Sozlamalarni yozish

```bash
cp .env.example .env
nano .env
```

Quyidagilarni to'ldiring:

| Qator            | Nima yoziladi                                                    |
|------------------|------------------------------------------------------------------|
| `BOT_TOKEN`      | @BotFather bergan token                                          |
| `BOT_USERNAME`   | botning username'i, `@` siz                                       |
| `ROOT_ADMIN_ID`  | sizning Telegram ID raqamingiz                                   |
| `DB_PASSWORD`    | baza uchun o'ylab topilgan kuchli parol (keyin o'zgartirmang)    |
| `BOT_GROUP_LINK` | ixtiyoriy: kontent ostida ko'rinadigan guruh, masalan `@mygroup` |

`DB_URL` va `DB_USERNAME` qatorlariga tegmang — Docker ularni o'zi sozlaydi.

Saqlash: `Ctrl+O`, `Enter`, chiqish: `Ctrl+X`.

## 6. Ishga tushirish

```bash
docker compose up -d --build
```

Birinchi marta 3–5 daqiqa davom etadi. Keyin loglarni ko'ring:

```bash
docker compose logs -f bot
```

`Started MovieBotApplication` yozuvi chiqsa — bot ishlayapti. Loglardan chiqish: `Ctrl+C` (bot to'xtamaydi).

Telegram'da botingizga `/start` yozing — siz admin bo'lganingiz uchun admin panel ochiladi.

## Kundalik ishlar

| Nima kerak              | Buyruq                                   |
|-------------------------|------------------------------------------|
| Loglarni ko'rish        | `docker compose logs -f bot`             |
| Botni qayta ishga tushirish | `docker compose restart bot`         |
| To'xtatish              | `docker compose down`                    |
| Yangi versiyaga o'tish  | `git pull && docker compose up -d --build` |

Server qayta yonsa, bot o'zi ishga tushadi.

## Zaxira nusxalar

Har kuni baza nusxasi `backups/` papkasiga saqlanadi, oxirgi 7 kunligi qoladi (`.env` da `BACKUP_KEEP_DAYS` bilan o'zgartirish mumkin).

Zaxiralarni vaqti-vaqti bilan serverdan tashqariga ham ko'chirib qo'ying — server butunlay ishdan chiqsa, ichidagi zaxiralar ham yo'qoladi.

Zaxiradan tiklash README'dagi **Restoring a backup** bo'limida yozilgan.

## Muammolar

- **`.env faylida ... yozilmagan`** — `.env` dagi o'sha qator bo'sh qolgan.
- **Logda `401 Unauthorized`** — token noto'g'ri, @BotFather'dan qayta nusxalang.
- **Logda `409 Conflict`** — shu token bilan bot boshqa joyda ham ishlab turibdi; ulardan birini to'xtating.
- **Majburiy kanal qo'shilmayapti** — botni o'sha kanalga **admin** qilib qo'shing.
