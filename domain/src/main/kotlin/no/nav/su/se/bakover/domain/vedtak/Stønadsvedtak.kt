package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.behandling.Stønadsbehandling
import no.nav.su.se.bakover.domain.beregning.Beregning
import økonomi.domain.simulering.Simulering

/**
 * Vedtak som er knyttet til:
 * - en stønadsperiode (søknadsbehandlinger)
 * - en periode som kan være deler av en stønadsperiode eller på tvers av stønadsperioder (revurdering)
 */
sealed interface Stønadsvedtak : Vedtak {
    val periode: Periode
    val behandling: Stønadsbehandling
    val beregning: Beregning? get() = behandling.beregning
    val simulering: Simulering? get() = behandling.simulering

    val utbetalingId: UUID30?

    val sakId get() = behandling.sakId
    val fnr get() = behandling.fnr
    val saksnummer get() = behandling.saksnummer
    val sakstype get() = behandling.sakstype

    fun erInnvilget(): Boolean
    fun erOpphør(): Boolean
    fun erStans(): Boolean
    fun erGjenopptak(): Boolean

    /**
     * Kun true dersom vi skal sende brev og brevet ikke er generert enda.
     */
    fun skalGenerereDokumentVedFerdigstillelse(): Boolean
}

sealed interface KunneIkkeGenerereSkattedokument {

    data object FeilVedGenereringAvDokument : KunneIkkeGenerereSkattedokument

    data object SkattegrunnlagErIkkeHentetForÅGenereDokument : KunneIkkeGenerereSkattedokument
}
