package behandling.domain

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt

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
    fun leggTilNyeHendelser(saksbehandlingsHendelse: NonEmptyList<T>): Saksbehandlingshistorikk<T>
}
