package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
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
 * - [VilkårsvurdertKlage.Bekreftet.Avvist] -> [VilkårsvurdertKlage] og [AvvistKlage.Påbegynt]
 *
 * - [VurdertKlage.Påbegynt] -> [VilkårsvurdertKlage] og [VurdertKlage.Påbegynt] og [VurdertKlage.Utfylt]
 * - [VurdertKlage.Utfylt] -> [VilkårsvurdertKlage] og [VurdertKlage]
 * - [VurdertKlage.Bekreftet] -> [VilkårsvurdertKlage] og [VurdertKlage] og [KlageTilAttestering]
 *
 * - [AvvistKlage.Påbegynt] -> [AvvistKlage.Bekreftet] og [VilkårsvurdertKlage]
 * - [AvvistKlage.Bekreftet] -> [KlageTilAttestering.Avvist] og [VilkårsvurdertKlage]
 *
 * - [KlageTilAttestering.Vurdert] -> [OversendtKlage] og [VurdertKlage.Bekreftet]
 * - [KlageTilAttestering.Avvist] -> [IverksattAvvistKlage] og [AvvistKlage.Bekreftet]
 *
 * - [OversendtKlage] -> ingen
 * - [IverksattAvvistKlage] -> ingen
 */
sealed interface Klage {
    val id: UUID
    val opprettet: Tidspunkt
    val sakId: UUID
    val saksnummer: Saksnummer
    val fnr: Fnr
    val journalpostId: JournalpostId
    val oppgaveId: OppgaveId
    val datoKlageMottatt: LocalDate
    val saksbehandler: NavIdentBruker.Saksbehandler
    val klagevedtakshistorikk: Klagevedtakshistorikk

    fun erÅpen(): Boolean {
        return when (this) {
            is OpprettetKlage,
            is VilkårsvurdertKlage,
            is VurdertKlage,
            is AvvistKlage,
            is KlageTilAttestering,
            -> true

            is IverksattAvvistKlage,
            is OversendtKlage,
            -> false
        }
    }

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
     * Dersom vi allerede har vurderinger vil vi ta vare på disse videre.
     * @return [VilkårsvurdertKlage.Påbegynt] eller [VilkårsvurdertKlage.Utfylt]
     */
    fun vilkårsvurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkårsvurderinger: VilkårsvurderingerTilKlage,
    ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
        return KunneIkkeVilkårsvurdereKlage.UgyldigTilstand(this::class, VilkårsvurdertKlage::class).left()
    }

    /** @return [VilkårsvurdertKlage.Bekreftet]  */
    fun bekreftVilkårsvurderinger(
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBekrefteKlagesteg.UgyldigTilstand, VilkårsvurdertKlage.Bekreftet> {
        return KunneIkkeBekrefteKlagesteg.UgyldigTilstand(this::class, VilkårsvurdertKlage.Bekreftet::class).left()
    }

    /** @return [VurdertKlage.Påbegynt] eller [VurdertKlage.Utfylt] */
    fun vurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vurderinger: VurderingerTilKlage,
    ): Either<KunneIkkeVurdereKlage.UgyldigTilstand, VurdertKlage> {
        return KunneIkkeVurdereKlage.UgyldigTilstand(this::class, VurdertKlage::class).left()
    }

    /** @return [VurdertKlage.Bekreftet] */
    fun bekreftVurderinger(
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBekrefteKlagesteg.UgyldigTilstand, VurdertKlage.Bekreftet> {
        return KunneIkkeBekrefteKlagesteg.UgyldigTilstand(this::class, VurdertKlage.Bekreftet::class).left()
    }

    fun leggTilAvvistFritekstTilBrev(
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekst: String,
    ): Either<KunneIkkeLeggeTilFritekstForAvvist.UgyldigTilstand, AvvistKlage> {
        return KunneIkkeLeggeTilFritekstForAvvist.UgyldigTilstand(this::class, AvvistKlage::class).left()
    }

    /** @return [KlageTilAttestering] */
    fun sendTilAttestering(
        saksbehandler: NavIdentBruker.Saksbehandler,
        opprettOppgave: () -> Either<KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave, OppgaveId>,
    ): Either<KunneIkkeSendeTilAttestering, KlageTilAttestering> {
        return KunneIkkeSendeTilAttestering.UgyldigTilstand(this::class, KlageTilAttestering::class).left()
    }

    /** @return [VurdertKlage.Bekreftet] eller [AvvistKlage.Bekreftet] */
    fun underkjenn(
        underkjentAttestering: Attestering.Underkjent,
        opprettOppgave: () -> Either<KunneIkkeUnderkjenne.KunneIkkeOppretteOppgave, OppgaveId>,
    ): Either<KunneIkkeUnderkjenne, Klage> {
        return KunneIkkeUnderkjenne.UgyldigTilstand(this::class, VurdertKlage.Bekreftet::class).left()
    }

    fun oversend(
        iverksattAttestering: Attestering.Iverksatt,
    ): Either<KunneIkkeOversendeKlage, OversendtKlage> {
        return KunneIkkeOversendeKlage.UgyldigTilstand(this::class, OversendtKlage::class).left()
    }

    fun leggTilNyttKlagevedtak(
        uprosessertKlageinstansVedtak: UprosessertKlageinstansvedtak,
        lagOppgaveCallback: () -> Either<KunneIkkeLeggeTilNyttKlageinstansVedtak, OppgaveId>,
    ): Either<KunneIkkeLeggeTilNyttKlageinstansVedtak, Klage> {
        return KunneIkkeLeggeTilNyttKlageinstansVedtak.MåVæreEnOversendtKlage(menVar = this::class).left()
    }
    sealed interface KunneIkkeLeggeTilNyttKlageinstansVedtak {
        data class MåVæreEnOversendtKlage(val menVar: KClass<out Klage>) : KunneIkkeLeggeTilNyttKlageinstansVedtak
        object IkkeStøttetUtfall : KunneIkkeLeggeTilNyttKlageinstansVedtak
        object KunneIkkeHenteAktørId : KunneIkkeLeggeTilNyttKlageinstansVedtak
        data class KunneIkkeLageOppgave(val feil: OppgaveFeil) : KunneIkkeLeggeTilNyttKlageinstansVedtak
    }

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
            return OpprettetKlage.create(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                datoKlageMottatt = datoKlageMottatt,
                saksbehandler = saksbehandler,
                klagevedtakshistorikk = Klagevedtakshistorikk.empty(),
            )
        }
    }
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

sealed class KunneIkkeBekrefteKlagesteg {
    object FantIkkeKlage : KunneIkkeBekrefteKlagesteg()
    data class UgyldigTilstand(val fra: KClass<out Klage>, val til: KClass<out Klage>) :
        KunneIkkeBekrefteKlagesteg()
}
