-- Xato yozilgan nomlar uchun "Balki shuni qidirgandirsiz" takliflari (pg_trgm).
-- Ba'zi xosting bazalarida kengaytma o'rnatishga ruxsat bo'lmaydi: unda migratsiya yiqilmaydi,
-- bot shunchaki takliflarsiz ishlaydi (kod buni hisobga oladi).
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
EXCEPTION
    WHEN insufficient_privilege OR feature_not_supported OR undefined_file THEN
        RAISE NOTICE 'pg_trgm o''rnatilmadi, o''xshash nomlar taklifi o''chiq bo''ladi';
END
$$;
