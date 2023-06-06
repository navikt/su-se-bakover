package no.nav.su.se.bakover.domain.sak

import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling

/**
 * Extension functions for Sak.kt som angår behandlinger.
 * Dette er for å unngå at Sak.kt blir for stor.
 */

fun Sak.nySøknadsbehandling(søknadsbehandling: Søknadsbehandling): Sak {
    return this.copy(behandlinger = this.behandlinger.nySøknadsbehandling(søknadsbehandling))
}

fun Sak.oppdaterSøknadsbehandling(søknadsbehandling: Søknadsbehandling): Sak {
    return this.copy(behandlinger = this.behandlinger.oppdaterSøknadsbehandling(søknadsbehandling))
}

fun Sak.nyRevurdering(revurdering: AbstraktRevurdering): Sak {
    return this.copy(behandlinger = this.behandlinger.nyRevurdering(revurdering))
}

fun Sak.oppdaterRevurdering(revurdering: AbstraktRevurdering): Sak {
    return this.copy(behandlinger = this.behandlinger.oppdaterRevurdering(revurdering))
}

fun Sak.nyRegulering(regulering: Regulering): Sak {
    return this.copy(behandlinger = this.behandlinger.nyRegulering(regulering))
}

fun Sak.oppdaterRegulering(regulering: Regulering): Sak {
    return this.copy(behandlinger = this.behandlinger.oppdaterRegulering(regulering))
}

fun Sak.nyKlage(klage: Klage): Sak {
    return this.copy(behandlinger = this.behandlinger.nyKlage(klage))
}

fun Sak.oppdaterKlage(klage: Klage): Sak {
    return this.copy(behandlinger = this.behandlinger.oppdaterKlage(klage))
}
