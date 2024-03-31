package no.nav.su.se.bakover.domain.vedtak

import behandling.søknadsbehandling.domain.avslag.ErAvslag
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import java.time.Clock

/**
 * Et avslagsvedtak fører ikke til endring i ytelsen.
 * Derfor vil et avslagsvedtak sin "stønadsperiode" kunne overlappe tidligere avslagsvedtak og andre vedtak som påvirker ytelsen.<br>
 *
 * [GjeldendeVedtaksdata] tar ikke hensyn til avslagsvedtak per tidspunkt, siden de ikke påvirker selve ytelsen.
 * Så hvis vi på et tidspunkt skal kunne revurdere/omgjøre disse vedtakene, så kan man ikke blindt arve [VedtakSomKanRevurderes].
 */
sealed interface Avslagsvedtak : VedtakIverksattSøknadsbehandling, ErAvslag {
    override val periode: Periode
    override val behandling: IverksattSøknadsbehandling.Avslag

    /**
     * Sender alltid brev ved avslag
     */
    override val skalSendeBrev: Boolean get() = behandling.skalSendeVedtaksbrev()

    /**
     * Om ny behandling kan bli startet basert på vedtakets behandling.
     *
     * Denne sjekker ikke på om det allerede finnes en åpen behandling fra før
     */
    val kanStarteNyBehandling: Boolean get() = behandling.erSøknadÅpen

    companion object {
        fun fromSøknadsbehandlingMedBeregning(
            avslag: IverksattSøknadsbehandling.Avslag.MedBeregning,
            clock: Clock,
        ) = VedtakAvslagBeregning.from(
            avslag = avslag,
            clock = clock,
        )

        fun fromSøknadsbehandlingUtenBeregning(
            avslag: IverksattSøknadsbehandling.Avslag.UtenBeregning,
            clock: Clock,
        ) = VedtakAvslagVilkår.from(
            avslag = avslag,
            clock = clock,
        )
    }
}
