alter table avstemming add column type text;

update avstemming set type = 'GRENSESNITT';

alter table avstemming alter column type set not null;