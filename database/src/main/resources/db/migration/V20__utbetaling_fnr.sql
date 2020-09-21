alter table utbetaling
    add column if not exists
        fnr varchar(11) not null default 'fnr';

update utbetaling
    set fnr = sak.fnr
from (select s.fnr as fnr, o.id as oppdragid from sak s
    join oppdrag o on o.sakid = s.id
) as sak
where utbetaling.oppdragid = sak.oppdragid;

alter table utbetaling
    alter column fnr drop default;
