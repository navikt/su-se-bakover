package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.Person
import java.time.Clock
import java.time.LocalDate
import kotlin.reflect.KClass

data class IverksattAvvistKlage(
    private val forrigeSteg: KlageTilAttestering.Avvist,
    override val attesteringer: Attesteringshistorikk,
) : Klage, AvvistKlageFelter by forrigeSteg {

    override fun erÅpen() = false

    override fun getFritekstTilBrev(): Either<KunneIkkeHenteFritekstTilBrev.UgyldigTilstand, String> {
        return fritekstTilVedtaksbrev.right()
    }

    /**
     * Vi har ikke lagt til noen valgmulighet for ikke å sende brev ved avvisning av klage.
     */
    fun skalSendeVedtaksbrev(): Boolean {
        return true
    }

    fun genererAvvistVedtaksbrev(
        hentNavnForNavIdent: (saksbehandler: NavIdentBruker) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
        hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
        clock: Clock,
    ): Either<KunneIkkeLageBrevRequestForKlage, LagBrevRequest.Klage> {
        return LagBrevRequest.Klage.Avvist(
            person = hentPerson(this.fnr).getOrElse {
                return KunneIkkeLageBrevRequestForKlage.FeilVedHentingAvPerson(it).left()
            },
            dagensDato = LocalDate.now(clock),
            saksbehandlerNavn = hentNavnForNavIdent(this.saksbehandler).getOrElse {
                return KunneIkkeLageBrevRequestForKlage.FeilVedHentingAvSaksbehandlernavn(it).left()
            },
            attestantNavn = this.attesteringer.hentSisteAttestering().attestant.let { hentNavnForNavIdent(it) }
                .getOrElse { return KunneIkkeLageBrevRequestForKlage.FeilVedHentingAvAttestantnavn(it).left() },
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
    data object FantIkkeKlage : KunneIkkeIverksetteAvvistKlage
    data object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksetteAvvistKlage
    data object FeilVedLagringAvDokumentOgKlage : KunneIkkeIverksetteAvvistKlage
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeIverksetteAvvistKlage {
        val til = IverksattAvvistKlage::class
    }

    data class KunneIkkeLageBrev(val feil: KunneIkkeLageBrevForKlage) : KunneIkkeIverksetteAvvistKlage
    data class KunneIkkeLageBrevRequest(val feil: no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevRequestForKlage) :
        KunneIkkeIverksetteAvvistKlage
}
