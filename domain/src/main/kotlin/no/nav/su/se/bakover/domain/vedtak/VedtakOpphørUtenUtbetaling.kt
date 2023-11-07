package no.nav.su.se.bakover.domain.vedtak

import dokument.domain.Dokumenttilstand
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.dokument.dokumenttilstandForBrevvalg
import no.nav.su.se.bakover.domain.dokument.setDokumentTilstandBasertPåBehandlingHvisNull
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import økonomi.domain.simulering.Simulering
import java.time.Clock
import java.util.UUID

/**
 * ADVARSEL: Kun for historiske revurderinger (rene avkortingsvedtak uten utbetaling). Skal ikke brukes i nye revurderinger.
 *
 * Opphørsvedtak der vi ikke har sendt linjer til oppdrag.
 * Dette vil være tilfeller der hele perioden vi opphører allerede er utbetalt.
 */
data class VedtakOpphørUtenUtbetaling(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val behandling: IverksattRevurdering.Opphørt,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val attestant: NavIdentBruker.Attestant,
    override val periode: Periode,
    /**
     * TODO jah: Bør vi prøve fjerne den? Og bør da beregningen også fjernes fra tilhørende behandling?
     * */
    override val beregning: Beregning,
    override val simulering: Simulering,
    override val dokumenttilstand: Dokumenttilstand,
) : VedtakIngenEndringIYtelse, Opphørsvedtak, Revurderingsvedtak {
    override val avsluttetAv: NavIdentBruker = attestant
    override val utbetalingId = null

    init {
        require(periode == behandling.periode)
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
        ) = VedtakOpphørUtenUtbetaling(
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
        ) = VedtakOpphørUtenUtbetaling(
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
}
