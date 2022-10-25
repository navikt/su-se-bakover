package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.sak.Saksnummer
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Gyldige tilstandsoverganger klage:
 * - [OpprettetKlage] -> [VilkårsvurdertKlage.Påbegynt] og [VilkårsvurdertKlage.Utfylt]
 *
 * - [VilkårsvurdertKlage.Påbegynt] -> [VilkårsvurdertKlage.Påbegynt] og [VilkårsvurdertKlage.Utfylt]
 * - [VilkårsvurdertKlage.Utfylt] -> [VilkårsvurdertKlage.Bekreftet]
 * - [VilkårsvurdertKlage.Bekreftet.TilVurdering] -> [VilkårsvurdertKlage] og [VurdertKlage.Påbegynt] og [VurdertKlage.Utfylt]
 * - [VilkårsvurdertKlage.Bekreftet.Avvist] -> [VilkårsvurdertKlage] og [AvvistKlage]
 *
 * - [VurdertKlage.Påbegynt] -> [VilkårsvurdertKlage] og [VurdertKlage.Påbegynt] og [VurdertKlage.Utfylt]
 * - [VurdertKlage.Utfylt] -> [VilkårsvurdertKlage] og [VurdertKlage]
 * - [VurdertKlage.Bekreftet] -> [VilkårsvurdertKlage] og [VurdertKlage] og [KlageTilAttestering]
 *
 * - [AvvistKlage] -> [KlageTilAttestering.Avvist] og [VilkårsvurdertKlage.Bekreftet.Avvist]
 *
 * - [KlageTilAttestering.Vurdert] -> [OversendtKlage] og [VurdertKlage.Bekreftet]
 * - [KlageTilAttestering.Avvist] -> [IverksattAvvistKlage] og [AvvistKlage]
 *
 * - [OversendtKlage] -> ingen
 * - [IverksattAvvistKlage] -> ingen
 */
sealed interface Klage : Klagefelter {

    /**
     * Convenience funksjon for å slippe store when-blokker.
     * De fleste tilstandene har denne satt, men hvis ikke vil den være null.
     */
    val vilkårsvurderinger: VilkårsvurderingerTilKlage?

    /**
     * Convenience funksjon for å slippe store when-blokker.
     * Dersom attesteringer er [Attestering.Iverksatt] vil behandlingen være ferdistilt/avsluttet.
     * Dersom attesteringer er [Attestering.Underkjent] vil behandlingen fremdeles være åpen.
     * */
    val attesteringer: Attesteringshistorikk

    fun prøvHentSisteAttestering(): Attestering? = attesteringer.prøvHentSisteAttestering()
    fun prøvHentSisteAttestant(): NavIdentBruker.Attestant? = prøvHentSisteAttestering()?.attestant

    fun erÅpen(): Boolean

    /**
     * @return fritekst til brev uavhengig om det er en oversending eller avvisning.
     */
    fun getFritekstTilBrev(): Either<KunneIkkeHenteFritekstTilBrev.UgyldigTilstand, String> {
        return KunneIkkeHenteFritekstTilBrev.UgyldigTilstand(this::class).left()
    }

    fun lagBrevRequest(
        hentNavnForNavIdent: (saksbehandler: NavIdentBruker.Saksbehandler) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
        hentVedtakDato: (klageId: UUID) -> LocalDate?,
        hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
        clock: Clock,
    ): Either<KunneIkkeLageBrevRequest, LagBrevRequest.Klage> {
        return KunneIkkeLageBrevRequest.UgyldigTilstand(this::class).left()
    }

    /**
     * @return [VilkårsvurdertKlage.Påbegynt] eller [VilkårsvurdertKlage.Utfylt]
     */
    fun vilkårsvurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkårsvurderinger: VilkårsvurderingerTilKlage,
    ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
        return KunneIkkeVilkårsvurdereKlage.UgyldigTilstand(this::class).left()
    }

    /** @return [VilkårsvurdertKlage.Bekreftet]  */
    fun bekreftVilkårsvurderinger(
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBekrefteKlagesteg.UgyldigTilstand, VilkårsvurdertKlage.Bekreftet> {
        return KunneIkkeBekrefteKlagesteg.UgyldigTilstand(this::class, VilkårsvurdertKlage.Bekreftet::class).left()
    }

    /** @return [KlageTilAttestering] */
    fun sendTilAttestering(
        saksbehandler: NavIdentBruker.Saksbehandler,
        opprettOppgave: () -> Either<KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave, OppgaveId>,
    ): Either<KunneIkkeSendeTilAttestering, KlageTilAttestering> {
        return KunneIkkeSendeTilAttestering.UgyldigTilstand(this::class).left()
    }

    /** @return [VurdertKlage.Bekreftet] eller [AvvistKlage] */
    fun underkjenn(
        underkjentAttestering: Attestering.Underkjent,
        opprettOppgave: () -> Either<KunneIkkeUnderkjenne.KunneIkkeOppretteOppgave, OppgaveId>,
    ): Either<KunneIkkeUnderkjenne, Klage> {
        // TODO jah: Man kan også underkjenne til Avvist, så til vil variere basert på nåværende tilstand.
        return KunneIkkeUnderkjenne.UgyldigTilstand(this::class, VurdertKlage.Bekreftet::class).left()
    }

    /**
     * Brukes av frontend for å vite om en klage kan avsluttes eller ikke.
     * Det holder ikke å kun vurdere statusen derfor ligger denne nærmere domenet.
     */
    fun kanAvsluttes(): Boolean

    fun avslutt(
        saksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String,
        tidspunktAvsluttet: Tidspunkt,
    ): Either<KunneIkkeAvslutteKlage, AvsluttetKlage>

    companion object {
        fun ny(
            sakId: UUID,
            saksnummer: Saksnummer,
            fnr: Fnr,
            journalpostId: JournalpostId,
            oppgaveId: OppgaveId,
            saksbehandler: NavIdentBruker.Saksbehandler,
            datoKlageMottatt: LocalDate,
            clock: Clock,
        ): OpprettetKlage {
            return OpprettetKlage(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                datoKlageMottatt = datoKlageMottatt,
                saksbehandler = saksbehandler,
            )
        }
    }

    sealed interface KunneIkkeLeggeTilNyKlageinstansHendelse {
        data class MåVæreEnOversendtKlage(val menVar: KClass<out Klage>) : KunneIkkeLeggeTilNyKlageinstansHendelse
        object KunneIkkeHenteAktørId : KunneIkkeLeggeTilNyKlageinstansHendelse
        data class KunneIkkeLageOppgave(val feil: OppgaveFeil) : KunneIkkeLeggeTilNyKlageinstansHendelse
    }
}

interface KlageSomKanVurderes {
    fun vurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vurderinger: VurderingerTilKlage,
    ): VurdertKlage
}

interface KanBekrefteKlagevurdering {
    fun bekreftVurderinger(
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): VurdertKlage.Bekreftet
}

interface KanLeggeTilFritekstTilAvvistBrev {
    fun leggTilFritekstTilAvvistVedtaksbrev(
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekstTilAvvistVedtaksbrev: String,
    ): AvvistKlage
}

interface Klagefelter {
    val id: UUID
    val opprettet: Tidspunkt
    val sakId: UUID
    val saksnummer: Saksnummer
    val fnr: Fnr
    val journalpostId: JournalpostId
    val oppgaveId: OppgaveId
    val datoKlageMottatt: LocalDate
    val saksbehandler: NavIdentBruker.Saksbehandler
}

sealed interface KunneIkkeLageBrevRequest {
    object FeilVedHentingAvVedtakDato : KunneIkkeLageBrevRequest
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeLageBrevRequest
    data class FeilVedHentingAvPerson(val personFeil: KunneIkkeHentePerson) : KunneIkkeLageBrevRequest
    data class FeilVedHentingAvSaksbehandlernavn(val feil: KunneIkkeHenteNavnForNavIdent) : KunneIkkeLageBrevRequest
}

sealed interface KunneIkkeHenteFritekstTilBrev {
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeHenteFritekstTilBrev
}

sealed interface KunneIkkeBekrefteKlagesteg {
    object FantIkkeKlage : KunneIkkeBekrefteKlagesteg
    data class UgyldigTilstand(val fra: KClass<out Klage>, val til: KClass<out Klage>) :
        KunneIkkeBekrefteKlagesteg
}

sealed interface KunneIkkeAvslutteKlage {
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeAvslutteKlage {
        val til: KClass<AvsluttetKlage> = AvsluttetKlage::class
    }

    object FantIkkeKlage : KunneIkkeAvslutteKlage
}

fun List<Klage>.harEksisterendeJournalpostId(journalpostId: JournalpostId) =
    this.any { it.journalpostId == journalpostId && it !is AvsluttetKlage }
