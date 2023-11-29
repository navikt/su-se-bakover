alter table hendelse
    add constraint hendelse_fk
        foreign key (tidligerehendelseid) references hendelse (hendelseid);
