alter table revurdering add column if not exists attestering jsonb default null;

update revurdering
set attestering = json_build_object('type', 'Iverksatt', 'attestant', attestant)
where attestant is not null;

alter table revurdering drop column attestant;