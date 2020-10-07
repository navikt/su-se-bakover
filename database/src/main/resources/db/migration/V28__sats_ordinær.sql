update beregning set sats = REPLACE(sats, 'LAV', 'ORDINÆR') where beregning.sats = 'LAV';
update månedsberegning set sats = REPLACE(sats, 'LAV', 'ORDINÆR') where månedsberegning.sats = 'LAV';
