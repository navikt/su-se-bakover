package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.dokument.EksterneGrunnlag
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.visitor.Visitable

/**
 * Vedtak som er knyttet til:
 * - en stønadsperiode (søknadsbehandlinger)
 * - en periode som kan være deler av en stønadsperiode eller på tvers av stønadsperioder (revurdering)
 */
sealed interface Stønadsvedtak : Vedtak, Visitable<VedtakVisitor> {
    val periode: Periode
    val behandling: Behandling
    val eksterneGrunnlag: EksterneGrunnlag?
    val beregning: Beregning? get() = behandling.beregning
    val simulering: Simulering? get() = behandling.simulering

    val utbetalingId: UUID30?

    val sakId get() = behandling.sakId
    val fnr get() = behandling.fnr
    val saksnummer get() = behandling.saksnummer
    val sakstype get() = behandling.sakstype

    fun erOpphør(): Boolean
    fun erStans(): Boolean
    fun erGjenopptak(): Boolean

    /**
     * Kun true dersom vi skal sende brev og brevet ikke er generert enda.
     */
    fun skalGenerereDokumentVedFerdigstillelse(): Boolean

    /**
     * Dersom grunnlaget inneholder fradrag av typen [Fradragstype.AvkortingUtenlandsopphold] vet vi at vedtaket
     * aktivt avkorter ytelsen.
     */
    fun harPågåendeAvkorting(): Boolean {
        return behandling.grunnlagsdata.fradragsgrunnlag.any { it.fradragstype == Fradragstype.AvkortingUtenlandsopphold }
    }

    /**
     * Vedtaket fører med seg eg behov for avkorting av ytelsen i fremtiden.
     * Avkortingen av aktuelle beløp er enda ikke påbegynt, men behovet er identifisert.
     */
    fun harIdentifisertBehovForFremtidigAvkorting(): Boolean
}


sealed interface KunneIkkeGenerereSkattedokument {
    object Feil : KunneIkkeGenerereSkattedokument
}
