# Admin qo'llanmasi

Bu qo'llanma botni boshqaradigan admin uchun. Botni serverga o'rnatish [INSTALL_UZ.md](INSTALL_UZ.md) da yozilgan.

Hamma ish Telegram ichida, bot orqali qilinadi — alohida sayt yoki dastur kerak emas.

## Mundarija

1. [Admin bo'lish](#1-admin-bolish)
2. [Kino qo'shish](#2-kino-qoshish)
3. [Serial yoki anime qo'shish](#3-serial-yoki-anime-qoshish)
4. [Tahrirlash, o'chirish, fasl va qism qo'shish](#4-tahrirlash-ochirish-fasl-va-qism-qoshish)
5. [Janrlar](#5-janrlar)
6. [Majburiy (homiy) kanallar](#6-majburiy-homiy-kanallar)
7. [Reklama yuborish](#7-reklama-yuborish)
8. [Statistika](#8-statistika)
9. [Adminlar](#9-adminlar)
10. [Foydali maslahatlar](#10-foydali-maslahatlar)

---

## 1. Admin bo'lish

- `.env` dagi `ROOT_ADMIN_ID` ga yozilgan odam — **asosiy admin**. Uni o'chirib bo'lmaydi.
- Botga `/start` yozing — admin bo'lsangiz, **Admin panel** ochiladi.
- Admin bo'lgan odam uchun majburiy obuna so'ralmaydi.

Admin panel tugmalari:

| Tugma | Nima qiladi |
|---|---|
| 🎬 Kino/serial qo'shish | Yangi kino, serial yoki anime qo'shish |
| 📋 Ro'yxat | Barcha kontent: tahrirlash, o'chirish, fasllar, janrlar |
| 👤 Admin qo'shish | Yangi admin uchun taklif havolasi |
| 👥 Adminlar ro'yxati | Adminlar va ularni olib tashlash |
| 📢 Majburiy kanallar | Homiy kanallar, muddat va hisob |
| 📣 Reklama yuborish | Reklama tayyorlash, rejalashtirish, hisobotlar |
| 🎭 Janrlar | Janrlar ro'yxati va tarjimalari |
| 📊 Statistika | Foydalanuvchilar va ko'rishlar |

Istalgan bosqichda **🔙 Orqaga** tugmasi oldingi qadamga qaytaradi yoki jarayonni bekor qiladi.

---

## 2. Kino qo'shish

**🎬 Kino/serial qo'shish** ni bosing va bot so'raganlarini ketma-ket yuboring:

1. **Nomi** — masalan `Tinch okeani dahshatlari`.
2. **Kodi** — foydalanuvchi shu kod bilan topadi, masalan `A103`.
   - faqat lotin harflari, raqamlar, `-` va `_`, ko'pi bilan 20 belgi;
   - band kod qabul qilinmaydi (`a103` va `A103` bir xil hisoblanadi).
3. **Turi** — 🎬 Kino.
4. **Tavsifi** — qisqa mazmun. Juda uzun bo'lsa, kartochkada avtomatik qisqartiriladi.
5. **Poster** — rasm yuboring.
6. **Video** — videoni yuboring.

Oxirida bot **"Janrlarni belgilaysizmi?"** deb so'raydi (janrlar qo'shilgan bo'lsa) — 6-bo'limga qarang.

> Bot jarayon o'rtasida qayta ishga tushsa ham, to'xtagan joydan davom etasiz (24 soat ichida).

---

## 3. Serial yoki anime qo'shish

1–5-qadamlar kinodagi kabi, faqat **Turi**: 📺 Serial yoki 🇯🇵 Anime.
Keyin:

6. **Nechta fasl** — masalan `2`.
7. Har bir fasl uchun:
   - **fasl posteri** (rasm),
   - **nechta qism** — masalan `12`,
   - har bir qism videosini **tartib bilan** yuboring: 1-qism, 2-qism, …

Bot har safar qaysi fasl va qism kutayotganini yozib turadi.

**Bekor qilsangiz:** shu paytgacha yuklangan fasl va qismlar **saqlanib qoladi**. Faqat bitta ham qismi yo'q fasl o'chiriladi.

> Bitta faslda juda ko'p qism bo'lsa (95 dan ortiq), uni bir necha faslga bo'lib yuklang — Telegram bitta xabar ostida shuncha tugmani ko'rsata olmaydi.

---

## 4. Tahrirlash, o'chirish, fasl va qism qo'shish

**📋 Ro'yxat** → kerakli kino. Kartochkada:

| Tugma | Nima qiladi |
|---|---|
| ✏️ Tahrirlash | Nom, kod, tur, tavsif, poster va videoni qaytadan so'raydi. Poster yoki videoni o'zgartirmaslik uchun `/skip` yozing |
| 🗑 O'chirish | Tasdiqlagandan keyin kino foydalanuvchilarga ko'rinmay qoladi, kodi bo'shaydi |
| 🎭 Janrlar | Kinoga janr belgilash |
| ➕ Yangi fasl qo'shish | Mavjud serialga yangi fasl(lar) — serial va anime uchun |
| 📺 Fasllarni boshqarish | Faslni tanlab: 🖼 posterini almashtirish yoki ➕ bitta qism qo'shish (har hafta chiqadigan anime uchun qulay) |

---

## 5. Janrlar

**🎭 Janrlar** → **➕ Janr qo'shish** va bir nechtasini vergul bilan yozing:

```text
Action, Adventure, Comedy, Drama, Fantasy
```

**Kinoga janr belgilash:** 📋 Ro'yxat → kino → 🎭 Janrlar → janrlarni bosing (✅ belgisi qo'yiladi yoki olinadi) → ✔️ Tayyor.

**Tarjimalar:** foydalanuvchi o'z tilida ko'radi (masalan *Jangari / Боевик / Action*).
- Ro'yxatda 🌐 — uchala tilga tarjima qilingan, ⚠️ 2/3 — chala.
- Janrni bosing → ✏️ O'zbekcha / Русский / English → nomini yozing. `-` yuborsangiz, tarjima o'chadi va asosiy nom ko'rinadi.

Foydalanuvchi menyusidagi **🎭 Janrlar** da faqat ichida kino bor janrlar chiqadi.

---

## 6. Majburiy (homiy) kanallar

Foydalanuvchi kino ko'rishidan oldin shu kanallarga a'zo bo'lishi kerak. Homiylik — botdan pul topishning asosiy yo'li.

**Qo'shish:** **📢 Majburiy kanallar** → **➕ Kanal qo'shish**, keyin:

- **Ochiq kanal** — username yuboring: `@mychannel`.
- **Yopiq kanal** — kanal ID'sini yuboring: `-1001234567890`.
  - ID'ni bilish: kanalni [web.telegram.org/a](https://web.telegram.org/a) da oching, manzil qatoridagi `#` dan keyingi raqam.
  - Bot o'zi **zayavkali havola** yaratadi. Foydalanuvchi zayavka yuborgani yetarli — kanal egasi uni tasdiqlashi shart emas.

So'ng **muddat** tanlang: 1 / 3 / 7 / 15 / 30 kun, ♾ muddatsiz yoki kunlar sonini yozing.

⚠️ **Bot o'sha kanalda admin bo'lishi shart** (yopiq kanalda "Foydalanuvchilarni taklif qilish" huquqi bilan). Aks holda bot kanalni qo'shmaydi va sababini aytadi.

**Ro'yxatda har bir kanal uchun:**
- 👥 **Bot orqali** — bot so'raganidan keyin kanalga qo'shilgan **noyob** odamlar soni. Kanal egasiga ko'rsatadigan asosiy raqam shu.
- ⏳ muddat va necha kun qolgani.

**Muddat tugaganda** kanal avtomatik olib tashlanadi va barcha adminlarga yakuniy natija yuboriladi.
**Qo'lda olib tashlash:** kanal nomini bosing → tasdiqlang. Hisob ham o'chadi, shuning uchun raqamni oldin yozib oling.

---

## 7. Reklama yuborish

**📣 Reklama yuborish** → **➕ Yangi reklama**:

1. **Reklama xabarini yuboring** — matn, rasm, video, GIF, fayl, istalgan turdagi xabar. U foydalanuvchilarga aynan shu ko'rinishda boradi.
2. **Tugma** (ixtiyoriy) — havolani yuboring:
   - `@mykanal`
   - `Kanalga o'tish @mykanal`
   - `Saytga o'tish https://example.uz`

   Tugma kerak bo'lmasa — **➡️ Tugmasiz davom etish**.
3. Bot reklama **qanday ko'rinishini** ko'rsatadi va nechta odamga borishini aytadi.
4. Tanlang:
   - **✅ Hozir yuborish** — bir necha soniyada boshlanadi;
   - **🕒 Vaqtini belgilash** — `20:00` (bugun, o'tib ketgan bo'lsa ertaga), `30.10 20:00` yoki `30.10.2026 20:00`.

Yuborish tugagach hisobot keladi: ✅ yuborildi, 🚫 botni bloklagan, ❌ xato.

- **🗓 Rejalashtirilganlar** — kutayotgan reklamalar; ❌ bilan bekor qilinadi.
- **📈 Hisobotlar** — oxirgi 10 ta reklama natijalari.

⚠️ **Reklama yuborilib bo'lguncha asl xabarni chatdan o'chirmang** — reklama undan nusxalanadi.

> Reklama sekundiga ~25 ta xabar tezligida yuboriladi (Telegram cheklovi). 10 000 foydalanuvchiga taxminan 7 daqiqa. Bu vaqtda bot boshqalarga javob berishda davom etadi.
> Botni bloklaganlar keyingi reklamalarga kiritilmaydi; ular botga qaytib yozsa, yana qo'shiladi.

---

## 8. Statistika

**📊 Statistika**:

- **Foydalanuvchilar:** jami, xabar yetib boradiganlar, botni bloklaganlar.
- **Yangi qo'shilganlar** va **faol foydalanuvchilar:** bugun, 7 kun, 30 kun.
- **Kontent:** kino, serial, anime soni va jami ko'rishlar.

Reklama yoki homiylik narxini aytishda "xabar yetib boradiganlar" va "30 kunda faol" raqamlaridan foydalaning.

---

## 9. Adminlar

**Yangi admin qo'shish:** **👤 Admin qo'shish** → bot havola beradi (10 daqiqa amal qiladi) → havolani kerakli odamga yuboring → u bosgandan keyin sizga tasdiqlash so'rovi keladi → ✅ Tasdiqlash.

**Olib tashlash:** **👥 Adminlar ro'yxati** → ❌. Asosiy adminni va o'zingizni olib tashlab bo'lmaydi.

---

## 10. Foydali maslahatlar

- **Kodlarni tartibli bering**, masalan kino uchun `K101`, serial uchun `S201`, anime uchun `A301`. Foydalanuvchilar kodni kanal postlaridan ko'rib yozadi.
- **Kanalga post qo'yganda** kino kodini yozing va bot havolasini qo'shing: `Kod: K101 👉 @bot_username`.
- **Bot faqat siz yuklagan kontentni tarqatadi.** Faqat o'zingizga tegishli yoki tarqatishga huquqingiz bor kontentni yuklang.
- **Zaxira nusxalar** har kuni avtomatik olinadi (`backups/` papkasi). Vaqti-vaqti bilan ularni serverdan tashqariga ham ko'chirib qo'ying.
- **Muammo bo'lsa**, serverda `docker compose logs -f bot` buyrug'i bilan loglarni ko'ring.
