package no.nav.su.se.bakover.domain.brev.command

import behandling.revurdering.domain.Opphørsgrunn
import beregning.domain.Beregning
import dokument.domain.generer.Brevmal
import dokument.domain.generer.BrevmalCommand
import dokument.domain.generer.Felles
import dokument.domain.generer.PdfInnhold
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.jsonRequest.opphørsvedtakBrev
import no.nav.su.se.bakover.domain.brev.jsonRequest.revurderingAvInntektBrev

sealed interface IverksettRevurderingDokumentCommand : BrevmalCommand {

    override val saksbehandler: NavIdentBruker.Saksbehandler
    override val attestant: NavIdentBruker.Attestant?
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
        override val sakstype: Sakstype,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val attestant: NavIdentBruker.Attestant?,
        override val beregning: Beregning,
        override val fritekst: String?,
        override val harEktefelle: Boolean,
        override val forventetInntektStørreEnn0: Boolean,
        // TODO jah: Satsoversikt er en Dto og bør ikke ligge i domenet. Vi bør heller ha en egen klasse for å representere dette i domenet.
        val satsoversikt: Satsoversikt,
    ) : IverksettRevurderingDokumentCommand {
        override val mal: Brevmal<Inntekt> get() = revurderingAvInntektBrev
        override fun lagInnhold(felles: Felles): PdfInnhold = mal.lagInnhold(felles, this)
    }

    data class Opphør(
        override val fødselsnummer: Fnr,
        override val saksnummer: Saksnummer,
        override val sakstype: Sakstype,
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
    ) : IverksettRevurderingDokumentCommand {
        override val mal: Brevmal<Opphør> get() = opphørsvedtakBrev
        override fun lagInnhold(felles: Felles): PdfInnhold = mal.lagInnhold(felles, this)
    }
}
