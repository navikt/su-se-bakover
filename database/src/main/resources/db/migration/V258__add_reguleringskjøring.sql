create table reguleringskjøring
(
    id                                 uuid primary key,
    aar                                int       not null,
    type                               text      not null,
    dryrun                             boolean   not null,
    start_tid                          timestamp not null,
    saker_antall                       int       not null,
    saker_ikke_loepende                text      not null default '',
    saker_ikke_loepende_antall         int       not null,
    saker_allerede_reguelert           text      not null default '',
    saker_allerede_reguelert_antall    int       not null,
    saker_maa_revurderes               text      not null default '',
    saker_maa_revurderes_antall        int       not null,
    reguleringer_som_feilet            text      not null default '',
    reguleringer_som_feilet_antall     int       not null,
    reguleringer_allerede_aapen        text      not null default '',
    reguleringer_allerede_aapen_antall int       not null,
    reguleringer_manuell               text      not null default '',
    reguleringer_manuell_antall        int       not null,
    reguleringer_automatisk            text      not null default '',
    reguleringer_automatisk_antall     int       not null
);