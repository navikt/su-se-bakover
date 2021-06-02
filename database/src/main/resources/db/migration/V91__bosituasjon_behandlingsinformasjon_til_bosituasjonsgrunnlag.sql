with søknadsbehandling_behandlingsinformasjon as ( select
                       behandlingId,
                       opprettet,
                       fraOgMed,
                       tilOgMed,
                       epsFnr,
                       bosituasjon ->> 'begrunnelse' begrunnelse,
       case
           when (ektefelle ->> 'type' = 'IngenEktefelle' AND bosituasjon ->> 'delerBolig' = 'false') then 'ALENE'
           when (ektefelle ->> 'type' = 'IngenEktefelle' AND bosituasjon ->> 'delerBolig' = 'true') then 'MED_VOKSNE'
           when (ektefelle ->> 'type' = 'IngenEktefelle' AND bosituasjon ->> 'delerBolig' is null) then 'HAR_IKKE_VALGT_EPS'
           when (ektefelle ->> 'type' = 'Ektefelle' AND (ektefelle -> 'alder')::int >= 67) then 'EPS_67_ELLER_ELDRE'
           when (ektefelle ->> 'type' = 'Ektefelle' AND bosituasjon ->> 'ektemakeEllerSamboerUførFlyktning' = 'true') then 'EPS_UNDER_67_UFØR_FLYKTNING'
           when (ektefelle ->> 'type' = 'Ektefelle' AND bosituasjon ->> 'ektemakeEllerSamboerUførFlyktning' = 'false') then 'EPS_UNDER_67'
           when (ektefelle ->> 'type' = 'Ektefelle' AND bosituasjon ->> 'ektemakeEllerSamboerUførFlyktning' is null) then 'HAR_IKKE_VALGT_UFØR_FLYKTNING'
           end bosituasjonstype

from (select
             id behandlingId,
             opprettet as opprettet,
             ( stønadsperiode #>> '{periode, fraOgMed}')::date fraOgMed,
             ( stønadsperiode #>> '{periode, tilOgMed}')::date tilOgMed,
             behandlingsinformasjon -> 'ektefelle' ektefelle,
             behandlingsinformasjon -> 'bosituasjon' bosituasjon,
             behandlingsinformasjon #>> '{ektefelle, fnr}' epsFnr
      from behandling) b )

insert into grunnlag_bosituasjon (id, opprettet, behandlingid, fraogmed, tilogmed, bosituasjontype, eps_fnr, begrunnelse) (
    select uuid_generate_v4() id,
           opprettet as opprettet,
           behandlingId as behandlingId,
           fraOgMed,
           tilOgMed,
           bosituasjonstype,
           epsFnr,
           begrunnelse from søknadsbehandling_behandlingsinformasjon );

with revurdering_behandlingsinformasjon as ( select
                                                       revurderingId,
                                                       opprettet,
                                                       fraOgMed,
                                                       tilOgMed,
                                                       epsFnr,
                                                       bosituasjon ->> 'begrunnelse' begrunnelse,
                                                       case
                                                           when (ektefelle ->> 'type' = 'IngenEktefelle' AND bosituasjon ->> 'delerBolig' = 'false') then 'ALENE'
                                                           when (ektefelle ->> 'type' = 'IngenEktefelle' AND bosituasjon ->> 'delerBolig' = 'true') then 'MED_VOKSNE'
                                                           when (ektefelle ->> 'type' = 'IngenEktefelle' AND bosituasjon ->> 'delerBolig' is null) then 'HAR_IKKE_VALGT_EPS'
                                                           when (ektefelle ->> 'type' = 'Ektefelle' AND (ektefelle -> 'alder')::int >= 67) then 'EPS_67_ELLER_ELDRE'
                                                           when (ektefelle ->> 'type' = 'Ektefelle' AND bosituasjon ->> 'ektemakeEllerSamboerUførFlyktning' = 'true') then 'EPS_UNDER_67_UFØR_FLYKTNING'
                                                           when (ektefelle ->> 'type' = 'Ektefelle' AND bosituasjon ->> 'ektemakeEllerSamboerUførFlyktning' = 'false') then 'EPS_UNDER_67'
                                                           when (ektefelle ->> 'type' = 'Ektefelle' AND bosituasjon ->> 'ektemakeEllerSamboerUførFlyktning' is null) then 'HAR_IKKE_VALGT_UFØR_FLYKTNING'
                                                           end bosituasjonstype

                                                   from (select
                                                             id revurderingId,
                                                             opprettet as opprettet,
                                                             ( periode ->> 'fraOgMed')::date fraOgMed,
                                                             ( periode ->> 'tilOgMed')::date tilOgMed,
                                                             behandlingsinformasjon -> 'ektefelle' ektefelle,
                                                             behandlingsinformasjon -> 'bosituasjon' bosituasjon,
                                                             behandlingsinformasjon #>> '{ektefelle, fnr}' epsFnr
                                                         from revurdering) r )

insert into grunnlag_bosituasjon (id, opprettet, behandlingid, fraogmed, tilogmed, bosituasjontype, eps_fnr, begrunnelse) (
    select uuid_generate_v4() id,
           opprettet as opprettet,
           revurderingId as behandlingId,
           fraOgMed,
           tilOgMed,
           bosituasjonstype,
           epsFnr,
           begrunnelse from revurdering_behandlingsinformasjon
);