alter table behandling
    add column if not exists
        utbetalingId varchar(30) references utbetaling(id) on delete set null;

update behandling set
	utbetalingId = utbetaling.id
from (select id, behandlingId from utbetaling) as utbetaling
where behandling.id = utbetaling.behandlingId;

alter table utbetaling drop column if exists behandlingId;
