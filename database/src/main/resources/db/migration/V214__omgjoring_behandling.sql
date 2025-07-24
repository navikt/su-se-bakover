alter table behandling
    add column if not exists
        omgjoringsgrunn text;

alter table behandling
    add column if not exists
         omgjoringsaarsak text;