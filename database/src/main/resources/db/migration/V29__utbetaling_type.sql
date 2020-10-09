alter table utbetaling
    add column if not exists
        type text
            default null;

with linje as(
	select l.id, l.beløp, l.utbetalingId, (select beløp from utbetalingslinje forrige where forrige.id = l.forrigeUtbetalingslinjeId) as forrigeBeløp from utbetalingslinje l
) 
update utbetaling 
set type = (case
	when linje.beløp = 0 then 'STANS'
	when linje.forrigeBeløp = 0 then 'GJENOPPTA'
	else 'NY'
end) from linje
where linje.utbetalingId = utbetaling.id;