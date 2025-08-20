package no.nav.su.se.bakover.statistikk.behandling.revurdering

import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak as RevurderingsårsakDomain

fun RevurderingsårsakDomain.toResultatBegrunnelse(): String {
    return when (this.årsak) {
        RevurderingsårsakDomain.Årsak.MELDING_FRA_BRUKER -> RevurderingsårsakDto.MELDING_FRA_BRUKER
        RevurderingsårsakDomain.Årsak.INFORMASJON_FRA_KONTROLLSAMTALE -> RevurderingsårsakDto.INFORMASJON_FRA_KONTROLLSAMTALE
        RevurderingsårsakDomain.Årsak.DØDSFALL -> RevurderingsårsakDto.DØDSFALL
        RevurderingsårsakDomain.Årsak.ANDRE_KILDER -> RevurderingsårsakDto.ANDRE_KILDER
        RevurderingsårsakDomain.Årsak.REGULER_GRUNNBELØP -> RevurderingsårsakDto.REGULER_GRUNNBELØP
        RevurderingsårsakDomain.Årsak.MANGLENDE_KONTROLLERKLÆRING -> RevurderingsårsakDto.MANGLENDE_KONTROLLERKLÆRING
        RevurderingsårsakDomain.Årsak.MOTTATT_KONTROLLERKLÆRING -> RevurderingsårsakDto.MOTTATT_KONTROLLERKLÆRING
        RevurderingsårsakDomain.Årsak.STANSET_VED_EN_FEIL -> RevurderingsårsakDto.STANSET_VED_EN_FEIL
        RevurderingsårsakDomain.Årsak.IKKE_MOTTATT_ETTERSPURT_DOKUMENTASJON -> RevurderingsårsakDto.IKKE_MOTTATT_ETTERSPURT_DOKUMENTASJON
        RevurderingsårsakDomain.Årsak.MIGRERT -> RevurderingsårsakDto.MIGRERT
        RevurderingsårsakDomain.Årsak.OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN -> RevurderingsårsakDto.OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN
        RevurderingsårsakDomain.Årsak.OMGJØRING_EGET_TILTAK -> RevurderingsårsakDto.OMGJØRING_EGET_TILTAK
        RevurderingsårsakDomain.Årsak.OMGJØRING_KLAGE -> RevurderingsårsakDto.OMGJØRING_KLAGE
        RevurderingsårsakDomain.Årsak.OMGJØRING_TRYGDERETTEN -> RevurderingsårsakDto.OMGJØRING_TRYGDERETTEN
    }.toString()
}

internal enum class RevurderingsårsakDto {
    MELDING_FRA_BRUKER,
    INFORMASJON_FRA_KONTROLLSAMTALE,
    DØDSFALL,
    ANDRE_KILDER,
    REGULER_GRUNNBELØP,
    MANGLENDE_KONTROLLERKLÆRING,
    MOTTATT_KONTROLLERKLÆRING,
    STANSET_VED_EN_FEIL,
    IKKE_MOTTATT_ETTERSPURT_DOKUMENTASJON,
    MIGRERT,
    OMGJØRING_VEDTAK_FRA_KLAGEINSTANSEN,
    OMGJØRING_EGET_TILTAK,
    OMGJØRING_KLAGE,
    OMGJØRING_TRYGDERETTEN,
}
