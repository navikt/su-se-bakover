package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.Person
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

data class IverksattAvvistKlage(
    private val forrigeSteg: KlageTilAttestering.Avvist,
    override val attesteringer: Attesteringshistorikk,
) : Klage, AvvistKlageFelter by forrigeSteg {

    override fun erÅpen() = false

    override fun getFritekstTilBrev(): Either<KunneIkkeHenteFritekstTilBrev.UgyldigTilstand, String> {
        return fritekstTilVedtaksbrev.right()
    }

    override fun lagBrevRequest(
        hentNavnForNavIdent: (saksbehandler: NavIdentBruker.Saksbehandler) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
        hentVedtaksbrevDato: (klageId: UUID) -> LocalDate?,
        hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
        clock: Clock,
    ): Either<KunneIkkeLageBrevRequest, LagBrevRequest.Klage> {
        return LagBrevRequest.Klage.Avvist(
            person = hentPerson(this.fnr).getOrHandle {
                return KunneIkkeLageBrevRequest.FeilVedHentingAvPerson(it).left()
            },
            dagensDato = LocalDate.now(clock),
            saksbehandlerNavn = hentNavnForNavIdent(this.saksbehandler).getOrHandle {
                return KunneIkkeLageBrevRequest.FeilVedHentingAvSaksbehandlernavn(it).left()
            },
            fritekst = this.fritekstTilVedtaksbrev,
            saksnummer = this.saksnummer,
        ).right()
    }

    override fun kanAvsluttes() = false
    override fun avslutt(
        saksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String,
        tidspunktAvsluttet: Tidspunkt,
    ) = KunneIkkeAvslutteKlage.UgyldigTilstand(this::class).left()
}

sealed interface KunneIkkeIverksetteAvvistKlage {
    object FantIkkeKlage : KunneIkkeIverksetteAvvistKlage
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksetteAvvistKlage
    object FeilVedLagringAvDokumentOgKlage : KunneIkkeIverksetteAvvistKlage
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeIverksetteAvvistKlage {
        val til = IverksattAvvistKlage::class
    }

    data class KunneIkkeLageBrev(val feil: KunneIkkeLageBrevForKlage) : KunneIkkeIverksetteAvvistKlage
    data class KunneIkkeLageBrevRequest(val feil: no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevRequest) :
        KunneIkkeIverksetteAvvistKlage
}
