package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dokument.domain.KunneIkkeLageDokument
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand
import kotlin.reflect.KClass

data class IverksattAvvistKlage(
    private val forrigeSteg: KlageTilAttestering.Avvist,
    override val attesteringer: Attesteringshistorikk,
    override val sakstype: Sakstype,
) : Klage,
    AvvistKlageFelter by forrigeSteg {

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

    fun lagAvvistVedtaksbrevKommando(): Either<KunneIkkeLageBrevKommandoForKlage, KlageDokumentCommand> {
        return KlageDokumentCommand.Avvist(
            fødselsnummer = this.fnr,
            saksnummer = this.saksnummer,
            sakstype = this.sakstype,
            saksbehandler = this.saksbehandler,
            attestant = this.attesteringer.hentSisteAttestering().attestant,
            fritekst = this.fritekstTilVedtaksbrev,
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

    data class KunneIkkeLageBrev(
        val feil: KunneIkkeLageDokument,
    ) : KunneIkkeIverksetteAvvistKlage

    data class KunneIkkeLageBrevRequest(
        val feil: KunneIkkeLageBrevKommandoForKlage,
    ) : KunneIkkeIverksetteAvvistKlage
}
