-- Finnes ikke klager for alder når denne kjøres i produksjon.
alter table klage
    add column if not exists sakstype text;

update klage set sakstype = 'uføre';

alter table klage alter column sakstype set not null;