package vedtak.domain

import beregning.domain.Beregning
import no.nav.su.se.bakover.behandling.Stønadsbehandling
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import økonomi.domain.simulering.Simulering
import java.util.UUID

/**
 * Per tidspunkt støtter vi ikke å revurdere Søknadsbehandlinger som førte til Avslag.
 * Når vi kan revurderer Avslag, må man passe på distinksjonen mellom vedtak som fører til endring i ytelsen når man finner gjeldende vedtak og tidslinjer.
 * TODO jah: Vi fjernet 'sealed' i forbindelse med flytting til vedtaksmodulen. Klarer vi legge på sealed igjen når vi har flyttet resten av vedtakene?
 */
interface VedtakSomKanRevurderes : Stønadsvedtak {
    override val id: UUID
    override val opprettet: Tidspunkt
    override val saksbehandler: NavIdentBruker.Saksbehandler
    override val attestant: NavIdentBruker.Attestant
    override val periode: Periode
    override val behandling: Stønadsbehandling
    override val beregning: Beregning?
    override val simulering: Simulering
    override val utbetalingId: UUID30?
    override val skalSendeBrev: Boolean get() = behandling.skalSendeVedtaksbrev()

    fun sakinfo(): SakInfo = behandling.sakinfo()

    companion object {
        /* Tillater extension functions av typen 'fun VedtakSomKanRevurderes.Companion...' */
    }
}
