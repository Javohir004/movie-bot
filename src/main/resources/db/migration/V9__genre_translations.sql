-- Janr nomlarining tarjimalari. name — admin yozgan asosiy nom (tarjima bo'lmasa shu chiqadi).
ALTER TABLE genres ADD COLUMN IF NOT EXISTS name_uz character varying(64);
ALTER TABLE genres ADD COLUMN IF NOT EXISTS name_ru character varying(64);
ALTER TABLE genres ADD COLUMN IF NOT EXISTS name_en character varying(64);

-- Keng tarqalgan janrlar uchun tayyor tarjimalar. Yangi janr qo'shilmaydi, faqat mavjudlari
-- (inglizcha yoki o'zbekcha nomi bo'yicha) to'ldiriladi; admin yozgan tarjima ustidan yozilmaydi.
UPDATE genres g
SET name_uz = coalesce(g.name_uz, t.uz),
    name_ru = coalesce(g.name_ru, t.ru),
    name_en = coalesce(g.name_en, t.en)
FROM (VALUES
        ('Action',        'Jangari',          'Боевик',              'Action'),
        ('Adventure',     'Sarguzasht',       'Приключения',         'Adventure'),
        ('Comedy',        'Komediya',         'Комедия',             'Comedy'),
        ('Drama',         'Drama',            'Драма',               'Drama'),
        ('Fantasy',       'Fantastika',       'Фэнтези',             'Fantasy'),
        ('Sci-Fi',        'Ilmiy fantastika', 'Научная фантастика',  'Sci-Fi'),
        ('Horror',        'Qo''rqinchli',     'Ужасы',               'Horror'),
        ('Thriller',      'Triller',          'Триллер',             'Thriller'),
        ('Romance',       'Romantika',        'Мелодрама',           'Romance'),
        ('Mystery',       'Sirli',            'Детектив',            'Mystery'),
        ('Crime',         'Jinoyat',          'Криминал',            'Crime'),
        ('Animation',     'Multfilm',         'Мультфильм',          'Animation'),
        ('Family',        'Oilaviy',          'Семейный',            'Family'),
        ('Historical',    'Tarixiy',          'Исторический',        'Historical'),
        ('Sports',        'Sport',            'Спорт',               'Sports'),
        ('Slice of Life', 'Kundalik hayot',   'Повседневность',      'Slice of Life'),
        ('Isekai',        'Isekai',           'Исекай',              'Isekai'),
        ('Mecha',         'Mexa',             'Меха',                'Mecha'),
        ('Supernatural',  'G''ayritabiiy',    'Сверхъестественное',  'Supernatural'),
        ('Psychological', 'Psixologik',       'Психологический',     'Psychological')
     ) AS t(en_key, uz, ru, en)
WHERE lower(g.name) = lower(t.en_key) OR lower(g.name) = lower(t.uz);
