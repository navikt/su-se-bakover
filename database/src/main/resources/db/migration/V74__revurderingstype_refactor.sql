update revurdering set revurderingstype = 'IVERKSATT_INNVILGET' where revurderingstype = 'IVERKSATT';
update revurdering set revurderingstype = 'TIL_ATTESTERING_INNVILGET' where revurderingstype = 'TIL_ATTESTERING';
update revurdering set revurderingstype = 'SIMULERT_INNVILGET' where revurderingstype = 'SIMULERT';
update revurdering set revurderingstype = 'UNDERKJENT_INNVILGET' where revurderingstype = 'UNDERKJENT';
