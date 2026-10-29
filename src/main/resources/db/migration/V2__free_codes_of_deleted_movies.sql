-- O'chirilgan (is_active = false) kinolar kodni band qilib turmasin.
-- Endi bot o'chirishda o'zi shunday qiladi, bu migratsiya undan oldin o'chirilganlarni tuzatadi.
-- Takror bajarilsa ham zarar qilmaydi: allaqachon belgilanganlar o'zgarmaydi.
UPDATE movies
SET code = code || '#deleted-' || id
WHERE is_active = false
  AND code NOT LIKE '%#deleted-%';

-- admin_sessions jadvali 1-bosqichda Hibernate orqali qo'shilgan edi.
-- Undan oldingi bazalarda u bo'lmasligi mumkin (V1 ularda baseline sabab bajarilmaydi).
CREATE TABLE IF NOT EXISTS admin_sessions (
    user_id      bigint                         NOT NULL PRIMARY KEY,
    pending_json text,
    state        character varying(255)         NOT NULL,
    updated_at   timestamp(6) without time zone NOT NULL
);
