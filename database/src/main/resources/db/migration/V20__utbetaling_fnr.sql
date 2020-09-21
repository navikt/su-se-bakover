alter table utbetaling
    add column if not exists
        fnr varchar(11) not null;
