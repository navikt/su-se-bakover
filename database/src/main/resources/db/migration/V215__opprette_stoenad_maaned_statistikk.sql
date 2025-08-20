CREATE TABLE stoenad_maaned_statistikk
(
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    maaned       DATE NOT NULL,
    vedtaksdato DATE NOT NULL,
    personnummer TEXT NOT NULL
);