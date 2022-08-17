-- nb: fjerner ikke avsluttet->fritekst i denne migreringen.

update revurdering set avsluttet = jsonb_set(avsluttet, '{brevvalg}', '{"type":"SKAL_SENDE_BREV_MED_FRITEKST"}') where avsluttet is not null and (avsluttet->>'fritekst') is not null;
update revurdering set avsluttet = jsonb_set(avsluttet, '{brevvalg}', '{"type":"SKAL_IKKE_SENDE_BREV"}') where avsluttet is not null and (avsluttet->>'fritekst') is null;

update revurdering set avsluttet = jsonb_insert(avsluttet, '{brevvalg, fritekst}', to_jsonb(avsluttet->>'fritekst'),true) where avsluttet is not null and (avsluttet->>'fritekst') is not null;
update revurdering set avsluttet = jsonb_insert(avsluttet, '{brevvalg, fritekst}', 'null',true) where avsluttet is not null and (avsluttet->>'fritekst') is null;

update revurdering set avsluttet = jsonb_insert(avsluttet, '{brevvalg, begrunnelse}', to_jsonb((avsluttet->>'begrunnelse')),true) where avsluttet is not null and (avsluttet->>'begrunnelse') is not null;
update revurdering set avsluttet = jsonb_insert(avsluttet, '{brevvalg, begrunnelse}', 'null',true) where avsluttet is not null and (avsluttet->>'begrunnelse') is null;

-- Testdata http://sqlfiddle.com/#!17/5831f5/11:
-- create table if not exists revurdering(id int primary key, avsluttet jsonb);
-- insert into revurdering (id,avsluttet) values (1,'{"begrunnelse":"b1","fritekst":"f1"}');
-- insert into revurdering (id,avsluttet) values (2,'{"begrunnelse":"b2","fritekst":null}');
-- insert into revurdering (id,avsluttet) values (3,null);