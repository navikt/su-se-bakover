create TYPE vilkår_vurdering_status AS ENUM ('OK','IKKE_OK','IKKE_VURDERT');
create TYPE vilkår AS ENUM ('UFØRHET','FLYKTNING','OPPHOLDSTILLATELSE','PERSONLIG_OPPMØTE','FORMUE','BOR_OG_OPPHOLDER_SEG_I_NORGE');

create table if not exists vilkårsvurdering
(
    id bigserial primary key,
    behandlingId bigint not null references behandling(id),
    vilkår vilkår not null,
    begrunnelse varchar,
    status vilkår_vurdering_status not null
);