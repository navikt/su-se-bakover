package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.dokument.Dokumenttilstand
import no.nav.su.se.bakover.domain.dokument.dokumenttilstandForBrevvalg
import no.nav.su.se.bakover.domain.dokument.setDokumentTilstandBasertPåBehandlingHvisNull
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import java.time.Clock
import java.util.UUID

/**
 * Opphørsvedtak der vi ikke har sendt linjer til oppdrag.
 * Dette vil være tilfeller der hele perioden vi opphører allerede er utbetalt.
 * Gjelder foreløpig kun avkorting.
 */
data class VedtakOpphørAvkorting(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val behandling: IverksattRevurdering.Opphørt,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val attestant: NavIdentBruker.Attestant,
    override val periode: Periode,
    /** Et avkortingsvedtak per 2023-05-03 er utelukkende basert på utenlandsoppholdvilkåret og beregningen vil være basert på tidligere vedtaks fradrag
     * TODO jah: Bør vi prøve fjerne den? Og bør da beregningen også fjernes fra tilhørende behandling?
     * */
    override val beregning: Beregning,
    /** Vil inneholde originalsimuleringen for hele perioden, uten å trekke fra avkortingsmånedene. */
    override val simulering: Simulering,
    override val dokumenttilstand: Dokumenttilstand,
) : VedtakIngenEndringIYtelse, Opphørsvedtak, Revurderingsvedtak {

    override val utbetalingId = null

    init {
        require(periode == behandling.periode)
        require(behandling.avkorting.skalAvkortes()) {
            "Kan ikke opprette opphørt vedtak (rent avkortingsvedtak) uten utbetalingId hvis vi ikke skal avkorte. Saksnummer $saksnummer"
        }
        require(behandling.avkorting.periode() == periode) {
            "Avkortingsvarselsperiode ${behandling.avkorting.periode()} må være lik vedtaket sin $periode"
        }
    }

    override fun skalGenerereDokumentVedFerdigstillelse(): Boolean {
        return when (dokumenttilstand) {
            Dokumenttilstand.SKAL_IKKE_GENERERE -> false.also {
                require(!behandling.skalSendeVedtaksbrev())
            }
            Dokumenttilstand.IKKE_GENERERT_ENDA -> true.also {
                require(behandling.skalSendeVedtaksbrev())
            }
            // Her har vi allerede generert brev fra før og ønsker ikke generere et til.
            Dokumenttilstand.GENERERT,
            Dokumenttilstand.JOURNALFØRT,
            Dokumenttilstand.SENDT,
            -> false
        }
    }

    companion object {

        fun from(
            revurdering: IverksattRevurdering.Opphørt,
            clock: Clock,
        ) = VedtakOpphørAvkorting(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            behandling = revurdering,
            periode = revurdering.periode,
            beregning = revurdering.beregning,
            simulering = revurdering.simulering,
            saksbehandler = revurdering.saksbehandler,
            attestant = revurdering.attestering.attestant,
            dokumenttilstand = revurdering.dokumenttilstandForBrevvalg(),
        )

        fun createFromPersistence(
            id: UUID,
            opprettet: Tidspunkt,
            behandling: IverksattRevurdering.Opphørt,
            saksbehandler: NavIdentBruker.Saksbehandler,
            attestant: NavIdentBruker.Attestant,
            periode: Periode,
            beregning: Beregning,
            simulering: Simulering,
            dokumenttilstand: Dokumenttilstand?,
        ) = VedtakOpphørAvkorting(
            id = id,
            opprettet = opprettet,
            behandling = behandling,
            saksbehandler = saksbehandler,
            attestant = attestant,
            periode = periode,
            beregning = beregning,
            simulering = simulering,
            dokumenttilstand = dokumenttilstand.setDokumentTilstandBasertPåBehandlingHvisNull(behandling),
        )
    }

    override fun accept(visitor: VedtakVisitor) {
        visitor.visit(this)
    }
}
