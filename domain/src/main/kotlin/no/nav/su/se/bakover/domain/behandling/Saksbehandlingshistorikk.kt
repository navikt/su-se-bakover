package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt

interface MedSaksbehandlerHistorikk<T : Saksbehandlingshendelse> {
    val søknadsbehandlingsHistorikk: Saksbehandlingshistorikk<T>
}

interface SaksbehandlingsHandling
interface Saksbehandlingshendelse {
    val tidspunkt: Tidspunkt
    val saksbehandler: NavIdentBruker.Saksbehandler
    val handling: SaksbehandlingsHandling
}

interface Saksbehandlingshistorikk<T : Saksbehandlingshendelse> {
    val historikk: List<T>
    fun leggTilNyHendelse(saksbehandlingsHendelse: T): Saksbehandlingshistorikk<T>
    fun leggTilNyeHendelser(saksbehandlingsHendelse: List<T>): Saksbehandlingshistorikk<T>
}
