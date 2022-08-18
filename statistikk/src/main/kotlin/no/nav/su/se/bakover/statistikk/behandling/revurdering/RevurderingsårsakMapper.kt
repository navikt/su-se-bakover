package no.nav.su.se.bakover.statistikk.behandling.revurdering

import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak as RevurderingsårsakDomain

fun RevurderingsårsakDomain.toResultatBegrunnelse(): String {
    return when (this.årsak) {
        RevurderingsårsakDomain.Årsak.MELDING_FRA_BRUKER -> Revurderingsårsak.MELDING_FRA_BRUKER
        RevurderingsårsakDomain.Årsak.INFORMASJON_FRA_KONTROLLSAMTALE -> Revurderingsårsak.INFORMASJON_FRA_KONTROLLSAMTALE
        RevurderingsårsakDomain.Årsak.DØDSFALL -> Revurderingsårsak.DØDSFALL
        RevurderingsårsakDomain.Årsak.ANDRE_KILDER -> Revurderingsårsak.ANDRE_KILDER
        RevurderingsårsakDomain.Årsak.REGULER_GRUNNBELØP -> Revurderingsårsak.REGULER_GRUNNBELØP
        RevurderingsårsakDomain.Årsak.MANGLENDE_KONTROLLERKLÆRING -> Revurderingsårsak.MANGLENDE_KONTROLLERKLÆRING
        RevurderingsårsakDomain.Årsak.MOTTATT_KONTROLLERKLÆRING -> Revurderingsårsak.MOTTATT_KONTROLLERKLÆRING
        RevurderingsårsakDomain.Årsak.IKKE_MOTTATT_ETTERSPURT_DOKUMENTASJON -> Revurderingsårsak.IKKE_MOTTATT_ETTERSPURT_DOKUMENTASJON
        RevurderingsårsakDomain.Årsak.MIGRERT -> Revurderingsårsak.MIGRERT
    }.toString()
}

internal enum class Revurderingsårsak {
    MELDING_FRA_BRUKER,
    INFORMASJON_FRA_KONTROLLSAMTALE,
    DØDSFALL,
    ANDRE_KILDER,
    REGULER_GRUNNBELØP,
    MANGLENDE_KONTROLLERKLÆRING,
    MOTTATT_KONTROLLERKLÆRING,
    IKKE_MOTTATT_ETTERSPURT_DOKUMENTASJON,
    MIGRERT;
}
