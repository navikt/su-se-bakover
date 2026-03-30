create table reguleringskjøring (
    id uuid primary key,
    aar int not null,
    type text not null,
    start_tid timestamp not null,
    antall_prosesserte_saker int not null,
    antall_reguleringer_laget int not null,
    antall_kunne_ikke_opprettes int not null
    );