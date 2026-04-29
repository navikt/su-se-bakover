alter table fradragssjekk_resultat_per_kjoring
    add column status text;

update fradragssjekk_resultat_per_kjoring
set status = resultat ->> 'status';

alter table fradragssjekk_resultat_per_kjoring
    alter column status set not null;
