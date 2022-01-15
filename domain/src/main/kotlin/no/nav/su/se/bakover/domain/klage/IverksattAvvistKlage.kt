package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

data class IverksattAvvistKlage private constructor(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val sakId: UUID,
    override val saksnummer: Saksnummer,
    override val fnr: Fnr,
    override val journalpostId: JournalpostId,
    override val oppgaveId: OppgaveId,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val datoKlageMottatt: LocalDate,
    override val klagevedtakshistorikk: Klagevedtakshistorikk,
    val attesteringer: Attesteringshistorikk,
    val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
    val fritekstTilBrev: String,
) : Klage {

    override fun getFritekstTilBrev(): Either<KunneIkkeHenteFritekstTilBrev.UgyldigTilstand, String> {
        return fritekstTilBrev.right()
    }

    override fun lagBrevRequest(
        hentNavnForNavIdent: (saksbehandler: NavIdentBruker.Saksbehandler) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
        hentVedtakDato: (klageId: UUID) -> LocalDate?,
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
            fritekst = this.fritekstTilBrev,
            saksnummer = this.saksnummer,
        ).right()
    }

    companion object {
        fun create(
            id: UUID,
            opprettet: Tidspunkt,
            sakId: UUID,
            saksnummer: Saksnummer,
            fnr: Fnr,
            journalpostId: JournalpostId,
            oppgaveId: OppgaveId,
            saksbehandler: NavIdentBruker.Saksbehandler,
            vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
            attesteringer: Attesteringshistorikk,
            datoKlageMottatt: LocalDate,
            fritekstTilBrev: String,
            klagevedtakshistorikk: Klagevedtakshistorikk,
        ): IverksattAvvistKlage {
            return IverksattAvvistKlage(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
                datoKlageMottatt = datoKlageMottatt,
                attesteringer = attesteringer,
                vilkårsvurderinger = vilkårsvurderinger,
                fritekstTilBrev = fritekstTilBrev,
                klagevedtakshistorikk = klagevedtakshistorikk,
            )
        }
    }
}

sealed interface KunneIkkeIverksetteAvvistKlage {
    object FantIkkeKlage : KunneIkkeIverksetteAvvistKlage
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksetteAvvistKlage
    object FeilVedLagringAvDokumentOgKlage : KunneIkkeIverksetteAvvistKlage
    data class UgyldigTilstand(val fra: KClass<out Klage>, val til: KClass<out Klage>) : KunneIkkeIverksetteAvvistKlage
    data class KunneIkkeLageBrev(val feil: KunneIkkeLageBrevForKlage) : KunneIkkeIverksetteAvvistKlage
    data class KunneIkkeLageBrevRequest(val feil: no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevRequest) :
        KunneIkkeIverksetteAvvistKlage
}
