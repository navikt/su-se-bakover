package no.nav.su.se.bakover.test.behandling

import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.sak.nyKlage

fun Sak.nyeKlager(klager: List<Klage>): Sak {
    return klager.fold(this) { sak, klage ->
        sak.nyKlage(klage)
    }
}
