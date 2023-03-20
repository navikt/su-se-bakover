package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.dokument.Dokumenttilstand
import no.nav.su.se.bakover.domain.dokument.dokumenttilstandForBrevvalg
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.BrevvalgRevurdering
import java.time.Clock
import java.util.UUID

data class VedtakGjenopptakAvYtelse private constructor(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val behandling: GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val attestant: NavIdentBruker.Attestant,
    override val periode: Periode,
    override val simulering: Simulering,
    override val utbetalingId: UUID30,
) : VedtakEndringIYtelse {

    override val beregning = null
    init {
        // Avhengige typer. Vi ønsker få feil dersom den endres.
        @Suppress("USELESS_IS_CHECK")
        require(behandling.brevvalgRevurdering is BrevvalgRevurdering.Valgt.IkkeSendBrev)
        require(periode == behandling.periode)
    }

    companion object {
        fun from(
            revurdering: GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse,
            utbetalingId: UUID30,
            clock: Clock,
        ): VedtakGjenopptakAvYtelse {
            return VedtakGjenopptakAvYtelse(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                behandling = revurdering,
                periode = revurdering.periode,
                simulering = revurdering.simulering,
                saksbehandler = revurdering.saksbehandler,
                attestant = revurdering.attesteringer.hentSisteAttestering().attestant,
                utbetalingId = utbetalingId,
            )
        }

        fun createFromPersistence(
            id: UUID,
            opprettet: Tidspunkt,
            behandling: GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse,
            saksbehandler: NavIdentBruker.Saksbehandler,
            attestant: NavIdentBruker.Attestant,
            periode: Periode,
            simulering: Simulering,
            utbetalingId: UUID30,
        ) = VedtakGjenopptakAvYtelse(
            id = id,
            opprettet = opprettet,
            behandling = behandling,
            saksbehandler = saksbehandler,
            attestant = attestant,
            periode = periode,
            simulering = simulering,
            utbetalingId = utbetalingId,
        )
    }

    override fun skalGenerereDokumentVedFerdigstillelse(): Boolean {
        return false
    }

    override val dokumenttilstand: Dokumenttilstand = behandling.dokumenttilstandForBrevvalg()

    override fun harIdentifisertBehovForFremtidigAvkorting() = false

    override fun accept(visitor: VedtakVisitor) {
        visitor.visit(this)
    }

    override fun erOpphør(): Boolean = false
    override fun erStans(): Boolean = false
    override fun erGjenopptak(): Boolean = true
}
