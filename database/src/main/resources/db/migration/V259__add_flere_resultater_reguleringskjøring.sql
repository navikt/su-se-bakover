alter table reguleringskjøring
    add column arsaker_regulering_ikke_opprettet text not null default '',
    add column antall_automatiske_reguleringer int not null default 0,
    add column antall_supplement_reguleringer int not null default 0,
    add column antall_reguleringer_manuell_behandling int not null default 0,
    add column arsaker_manuell_behandling text not null default '';