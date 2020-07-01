create TYPE VILKÅR_VURDERING_STATUS AS ENUM ('OK','IKKE_OK','IKKE_VURDERT');
create TYPE VILKÅR AS ENUM ('UFØRHET', 'FLYKTNING', 'OPPHOLDSTILLATELSE', 'PERSONLIG_OPPMØTE', 'FORMUE');

create table if not exists vilkårsvurdering
(
    id bigserial primary key,
    behandlingId bigint not null references behandling(id),
    vilkår VILKÅR not null,
    begrunnelse varchar,
    status VILKÅR_VURDERING_STATUS not null
);