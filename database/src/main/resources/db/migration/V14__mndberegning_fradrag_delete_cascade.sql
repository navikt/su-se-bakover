alter table månedsberegning
    drop constraint månedsberegning_beregningid_fkey;

alter table månedsberegning
    add foreign key (beregningId) references beregning(id) on delete cascade;

alter table fradrag
    drop constraint fradrag_beregningid_fkey;

alter table fradrag
    add foreign key (beregningId) references beregning(id) on delete cascade;