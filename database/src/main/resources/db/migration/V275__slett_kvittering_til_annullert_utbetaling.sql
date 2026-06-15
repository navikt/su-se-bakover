-- Se https://jira.adeo.no/browse/TOB-6767
delete from hendelse
where type = 'MOTTATT_UTBETALINGSKVITTERING'
and data->>'originalKvittering' like '%<henvisning>b1f7f79a-bf50-4b5b-956e-bd529a</henvisning>%'
and data->>'originalKvittering' like '%<delytelseId>71ad92c7-2a28-4545-a9a6-3b772a</delytelseId>%';