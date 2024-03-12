package no.nav.su.se.bakover.test.behandling

import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.regulering.Reguleringer
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.sak.nyKlage
import no.nav.su.se.bakover.domain.sak.nyRegulering
import no.nav.su.se.bakover.domain.sak.nyRevurdering
import no.nav.su.se.bakover.domain.sak.nySøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling

fun Sak.nyeSøknadsbehandlinger(søknadsbehandlinger: List<Søknadsbehandling>): Sak {
    return søknadsbehandlinger.fold(this) { sak, søknadsbehandling ->
        sak.nySøknadsbehandling(søknadsbehandling)
    }
}

fun Sak.nyeKlager(klager: List<Klage>): Sak {
    return klager.fold(this) { sak, klage ->
        sak.nyKlage(klage)
    }
}

fun Sak.nyeRevurderinger(revurderinger: List<Revurdering>): Sak {
    return revurderinger.fold(this) { sak, revurdering ->
        sak.nyRevurdering(revurdering)
    }
}

fun Sak.nyeReguleringer(reguleringer: Reguleringer): Sak {
    return reguleringer.fold(this) { sak, regulering ->
        sak.nyRegulering(regulering)
    }
}
