-- Dette feltet er i utgangspunktet kun tiltenkt IngenEndring, siden de andre vedtakene per nå alltid skal sende brev.
-- Dersom det skal utvides anbefaler vi at det lages egne tabeller/domenetyper for brev/utgående korrespondanse.
alter table revurdering add column if not exists skalFøreTilBrevutsending boolean default true not null;

update revurdering
set skalFøreTilBrevutsending = false
where revurderingstype in ('TIL_ATTESTERING_INGEN_ENDRING', 'IVERKSATT_INGEN_ENDRING', 'UNDERKJENT_INGEN_ENDRING');