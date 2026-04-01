alter table regulering drop column if exists aapBeregningSupplement;
alter table regulering add column if not exists eksternt_regulerte_belop jsonb;

