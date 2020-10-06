update behandling set status = REPLACE(status, 'BEREGNET', 'BEREGNET_INNVILGET') where behandling.status = 'BEREGNET';
update behandling set status = REPLACE(status, 'BEREGNET_INVILGET', 'BEREGNET_INNVILGET') where behandling.status = 'BEREGNET_INVILGET';
