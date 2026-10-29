-- Foydalanuvchi tanlagan til (uz, ru, en). Bo'sh bo'lsa, /start da tanlash so'raladi.
ALTER TABLE users ADD COLUMN IF NOT EXISTS language character varying(8);
