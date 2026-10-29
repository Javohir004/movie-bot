-- Hibernate enum ustunlariga CHECK cheklovlarini qo'ygan edi. Ular bo'lsa, enum'ga yangi qiymat
-- (masalan yangi admin holati) qo'shilganda baza uni rad etadi. Qiymatlarni kodning o'zi nazorat qiladi.
ALTER TABLE admin_invites  DROP CONSTRAINT IF EXISTS admin_invites_status_check;
ALTER TABLE admin_sessions DROP CONSTRAINT IF EXISTS admin_sessions_state_check;
ALTER TABLE movies         DROP CONSTRAINT IF EXISTS movies_type_check;
ALTER TABLE users          DROP CONSTRAINT IF EXISTS users_role_check;

-- Statistika uchun. DEFAULT qo'yilmaydi: aks holda eski foydalanuvchilar hammasi "bugun qo'shilgan"
-- bo'lib ko'rinadi. Eski yozuvlarda bo'sh qoladi, yangilarida kod o'zi to'ldiradi.
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at   timestamp(6) without time zone;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_seen_at timestamp(6) without time zone;
