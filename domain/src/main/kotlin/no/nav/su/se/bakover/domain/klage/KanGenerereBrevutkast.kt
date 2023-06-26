package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.Person
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

sealed interface KanGenerereBrevutkast : Klage {
    val fritekstTilVedtaksbrev: String

    /**
     * TODO jah: Splitt denne i en funksjon for attestant (denne tar eksplisitt inn en attestant) og en for saksbehandler (ingen parameter for attestant)
     * @param utførtAv - tar inn ident på den som utførte handlingen. Det er ikke sikkert denne identen havner i brevet.
     * @param hentVedtaksbrevDato dette er kun støttet for oversendelser og ikke avvisning. Se eget kort i Trello.
     */
    fun lagBrevRequest(
        utførtAv: NavIdentBruker,
        hentNavnForNavIdent: (saksbehandler: NavIdentBruker) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
        hentVedtaksbrevDato: (klageId: UUID) -> LocalDate?,
        hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
        clock: Clock,
    ): Either<KunneIkkeLageBrevRequestForKlage, LagBrevRequest.Klage>
}

// TODO jah: Mulig vi skal bruke et slags avvist konsept ala: AvvistVariant.genererAvvistVedtaksbrev()
internal fun KanGenerereBrevutkast.genererAvvistVedtaksbrev(
    attestant: NavIdentBruker.Attestant?,
    hentNavnForNavIdent: (saksbehandler: NavIdentBruker) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
    hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
    clock: Clock,
): Either<KunneIkkeLageBrevRequestForKlage, LagBrevRequest.Klage> {
    return LagBrevRequest.Klage.Avvist(
        person = hentPerson(this.fnr).getOrElse {
            return KunneIkkeLageBrevRequestForKlage.FeilVedHentingAvPerson(it).left()
        },
        dagensDato = LocalDate.now(clock),
        saksbehandlerNavn = hentNavnForNavIdent(saksbehandler).getOrElse {
            return KunneIkkeLageBrevRequestForKlage.FeilVedHentingAvSaksbehandlernavn(it).left()
        },
        attestantNavn = attestant?.let { hentNavnForNavIdent(it) }
            ?.getOrElse { return KunneIkkeLageBrevRequestForKlage.FeilVedHentingAvAttestantnavn(it).left() },
        fritekst = this.fritekstTilVedtaksbrev,
        saksnummer = this.saksnummer,
    ).right()
}

internal fun KanGenerereBrevutkast.genererOversendelsesBrev(
    attestant: NavIdentBruker.Attestant?,
    hentNavnForNavIdent: (saksbehandler: NavIdentBruker) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
    hentVedtaksbrevDato: (klageId: UUID) -> LocalDate?,
    hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
    clock: Clock,
): Either<KunneIkkeLageBrevRequestForKlage, LagBrevRequest.Klage> {
    return LagBrevRequest.Klage.Oppretthold(
        person = hentPerson(this.fnr).getOrElse {
            return KunneIkkeLageBrevRequestForKlage.FeilVedHentingAvPerson(it).left()
        },
        dagensDato = LocalDate.now(clock),
        saksbehandlerNavn = hentNavnForNavIdent(this.saksbehandler).getOrElse {
            return KunneIkkeLageBrevRequestForKlage.FeilVedHentingAvSaksbehandlernavn(it).left()
        },
        attestantNavn = attestant?.let { hentNavnForNavIdent(it) }
            ?.getOrElse { return KunneIkkeLageBrevRequestForKlage.FeilVedHentingAvAttestantnavn(it).left() },
        fritekst = this.fritekstTilVedtaksbrev,
        saksnummer = this.saksnummer,
        klageDato = this.datoKlageMottatt,
        vedtaksbrevDato = hentVedtaksbrevDato(this.id)
            ?: return KunneIkkeLageBrevRequestForKlage.FeilVedHentingAvVedtaksbrevDato.left(),
    ).right()
}
