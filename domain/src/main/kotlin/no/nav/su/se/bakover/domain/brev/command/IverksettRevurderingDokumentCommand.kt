package no.nav.su.se.bakover.domain.brev.command

import behandling.revurdering.domain.Opphørsgrunn
import beregning.domain.Beregning
import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.common.Månedsbeløp
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.beregning.Tilbakekreving

sealed interface IverksettRevurderingDokumentCommand : GenererDokumentCommand {

    val saksbehandler: NavIdentBruker.Saksbehandler
    val attestant: NavIdentBruker.Attestant?
    val beregning: Beregning
    val fritekst: String?
    val harEktefelle: Boolean
    val forventetInntektStørreEnn0: Boolean

    /**
     * Når vi revurderer en sak, så sender vi et enkeltvedtak.
     * Merk at saksbehandler kan overstyre dette valget, slik at vi ikke sender brev til bruker.
     */
    data class Inntekt(
        override val fødselsnummer: Fnr,
        override val saksnummer: Saksnummer,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val attestant: NavIdentBruker.Attestant?,
        override val beregning: Beregning,
        override val fritekst: String?,
        override val harEktefelle: Boolean,
        override val forventetInntektStørreEnn0: Boolean,
        // TODO jah: Satsoversikt er en Dto og bør ikke ligge i domenet. Vi bør heller ha en egen klasse for å representere dette i domenet.
        val satsoversikt: Satsoversikt,
    ) : IverksettRevurderingDokumentCommand

    /**
     * Når vi revurderer en sak, så sender vi et enkeltvedtak.
     * Merk at saksbehandler kan overstyre dette valget, slik at vi ikke sender brev til bruker.
     */
    data class TilbakekrevingAvPenger(
        val ordinærtRevurderingBrev: Inntekt,
        val tilbakekreving: Tilbakekreving,
        val satsoversikt: Satsoversikt,
    ) : IverksettRevurderingDokumentCommand by ordinærtRevurderingBrev {

        fun erstattBruttoMedNettoFeilutbetaling(netto: Månedsbeløp): TilbakekrevingAvPenger {
            return copy(tilbakekreving = Tilbakekreving(netto.månedbeløp))
        }
    }

    data class Opphør(
        override val fødselsnummer: Fnr,
        override val saksnummer: Saksnummer,
        override val beregning: Beregning,
        override val forventetInntektStørreEnn0: Boolean,
        override val harEktefelle: Boolean,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val attestant: NavIdentBruker.Attestant?,
        override val fritekst: String?,
        val opphørsgrunner: List<Opphørsgrunn>,
        val opphørsperiode: Periode,
        val satsoversikt: Satsoversikt,
        val halvtGrunnbeløp: Int,
    ) : IverksettRevurderingDokumentCommand
}
