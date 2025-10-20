@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Klage (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import behandling.klage.domain.KlageId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand
import java.time.LocalDate

sealed interface KanGenerereBrevutkast : Klage {
    val fritekstTilVedtaksbrev: String?

    /**
     * TODO jah: Splitt denne i en funksjon for attestant (denne tar eksplisitt inn en attestant) og en for saksbehandler (ingen parameter for attestant)
     * @param utførtAv - tar inn ident på den som utførte handlingen. Det er ikke sikkert denne identen havner i brevet.
     * @param hentVedtaksbrevDato dette er kun støttet for oversendelser og ikke avvisning. Se eget kort i Trello.
     */
    fun lagBrevRequest(
        utførtAv: NavIdentBruker,
        hentVedtaksbrevDato: (klageId: KlageId) -> LocalDate?,
    ): Either<KunneIkkeLageBrevKommandoForKlage, KlageDokumentCommand>
}

// TODO jah: Mulig vi skal bruke et slags avvist konsept ala: AvvistVariant.genererAvvistVedtaksbrev()
internal fun KanGenerereBrevutkast.lagAvvistVedtaksbrevKommando(
    attestant: NavIdentBruker.Attestant?,
): Either<KunneIkkeLageBrevKommandoForKlage, KlageDokumentCommand> {
    return KlageDokumentCommand.Avvist(
        fødselsnummer = this.fnr,
        saksnummer = this.saksnummer,
        sakstype = this.sakstype,
        saksbehandler = saksbehandler,
        attestant = attestant,
        fritekst = this.fritekstTilVedtaksbrev!!,
    ).right()
}

internal fun KanGenerereBrevutkast.genererOversendelsesBrev(
    attestant: NavIdentBruker.Attestant?,
    hentVedtaksbrevDato: (klageId: KlageId) -> LocalDate?,
): Either<KunneIkkeLageBrevKommandoForKlage, KlageDokumentCommand> {
    val vedtaksbrevDato = (
        hentVedtaksbrevDato(this.id)
            ?: return KunneIkkeLageBrevKommandoForKlage.FeilVedHentingAvVedtaksbrevDato.left()
        )

    val fritekst = when (this) {
        is AvvistKlage -> this.fritekstTilVedtaksbrev
        is KlageTilAttestering.Avvist -> this.fritekstTilVedtaksbrev
        is KlageTilAttestering.Vurdert -> this.fritekstTilVedtaksbrev
        is VurdertKlage.UtfyltOppretthold -> this.fritekstTilVedtaksbrev
        is VurdertKlage.Påbegynt -> this.fritekstTilVedtaksbrev ?: return KunneIkkeLageBrevKommandoForKlage.FritekstErIkkeFyltUt.left()
        is VurdertKlage.BekreftetOpprettholdt -> this.fritekstTilVedtaksbrev
    }

    return KlageDokumentCommand.Oppretthold(
        fødselsnummer = this.fnr,
        saksnummer = this.saksnummer,
        sakstype = this.sakstype,
        saksbehandler = saksbehandler,
        attestant = attestant,
        fritekst = fritekst,
        klageDato = this.datoKlageMottatt,
        vedtaksbrevDato = vedtaksbrevDato,
    ).right()
}
