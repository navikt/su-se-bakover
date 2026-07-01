create table if not exists kontrollsamtale_notat
(
    id uuid primary key,
    opprettet timestamptz not null,
    personligOppmøte boolean not null,
    fullmaktOgLegeerklæring boolean not null,
    originalPass boolean not null,
    harVærtUtenlands boolean not null,
    utenlandsoppholdDatoer jsonb not null,
    harPlanerOmUtenlandsreise boolean not null,
    planlagteUtenlandsreiseDatoer jsonb not null,
    reiseDokumentasjon boolean not null,
    økonomiskSituasjon boolean not null,
    andreForhold boolean not null,
    skatteopplysninger boolean not null
    );