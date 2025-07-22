alter table behandling
    add column if not exists
        omgjøringsgrunn text;

alter table behandling
    add column if not exists
        omgjøringsårsak text;