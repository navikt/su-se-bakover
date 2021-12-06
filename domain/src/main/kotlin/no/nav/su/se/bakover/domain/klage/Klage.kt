package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Gyldige tilstandsoverganger klage:
 * - [OpprettetKlage] -> [VilkårsvurdertKlage.Påbegynt] og [VilkårsvurdertKlage.Utfylt]
 * - [VilkårsvurdertKlage.Påbegynt] -> [VilkårsvurdertKlage.Påbegynt] og [VilkårsvurdertKlage.Utfylt]
 * - [VilkårsvurdertKlage.Utfylt] -> [VilkårsvurdertKlage]
 * - [VilkårsvurdertKlage.Bekreftet] -> [VilkårsvurdertKlage] og [VurdertKlage.Påbegynt] og [VurdertKlage.Utfylt]
 * - [VurdertKlage.Påbegynt] -> [VilkårsvurdertKlage] og [VurdertKlage.Påbegynt] og [VurdertKlage.Utfylt]
 * - [VurdertKlage.Utfylt] -> [VilkårsvurdertKlage] og [VurdertKlage]
 * - [VurdertKlage.Bekreftet] -> [VilkårsvurdertKlage] og [VurdertKlage] og [KlageTilAttestering]
 * - [KlageTilAttestering] -> [IverksattKlage] og [VurdertKlage.Bekreftet]
 * - [IverksattKlage] -> ingen
 */
sealed class Klage {
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val sakId: UUID
    abstract val saksnummer: Saksnummer
    abstract val fnr: Fnr
    abstract val journalpostId: JournalpostId
    abstract val oppgaveId: OppgaveId
    abstract val datoKlageMottatt: LocalDate
    abstract val saksbehandler: NavIdentBruker.Saksbehandler

    fun erÅpen(): Boolean {
        return this !is IverksattKlage
    }

    /**
     * Dersom vi allerede har vurderinger vil vi ta vare på disse videre.
     * @return [VilkårsvurdertKlage.Påbegynt] eller [VilkårsvurdertKlage.Utfylt]
     */
    open fun vilkårsvurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkårsvurderinger: VilkårsvurderingerTilKlage,
    ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
        return KunneIkkeVilkårsvurdereKlage.UgyldigTilstand(this::class, VilkårsvurdertKlage::class).left()
    }

    /** @return [VilkårsvurdertKlage.Bekreftet]  */
    open fun bekreftVilkårsvurderinger(
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBekrefteKlagesteg.UgyldigTilstand, VilkårsvurdertKlage.Bekreftet> {
        return KunneIkkeBekrefteKlagesteg.UgyldigTilstand(this::class, VilkårsvurdertKlage.Bekreftet::class).left()
    }

    /** @return [VurdertKlage.Påbegynt] eller [VurdertKlage.Utfylt] */
    open fun vurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vurderinger: VurderingerTilKlage,
    ): Either<KunneIkkeVurdereKlage.UgyldigTilstand, VurdertKlage> {
        return KunneIkkeVurdereKlage.UgyldigTilstand(this::class, VurdertKlage::class).left()
    }

    /** @return [VurdertKlage.Bekreftet] */
    open fun bekreftVurderinger(
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBekrefteKlagesteg.UgyldigTilstand, VurdertKlage.Bekreftet> {
        return KunneIkkeBekrefteKlagesteg.UgyldigTilstand(this::class, VurdertKlage.Bekreftet::class).left()
    }

    /** @return [KlageTilAttestering] */
    open fun sendTilAttestering(
        saksbehandler: NavIdentBruker.Saksbehandler,
        opprettOppgave: () -> Either<KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave, OppgaveId>,
    ): Either<KunneIkkeSendeTilAttestering, KlageTilAttestering> {
        return KunneIkkeSendeTilAttestering.UgyldigTilstand(this::class, KlageTilAttestering::class).left()
    }

    /** @return [VurdertKlage.Bekreftet] */
    open fun underkjenn(
        underkjentAttestering: Attestering.Underkjent,
        opprettOppgave: () -> Either<KunneIkkeUnderkjenne.KunneIkkeOppretteOppgave, OppgaveId>,
    ): Either<KunneIkkeUnderkjenne, VurdertKlage.Bekreftet> {
        return KunneIkkeUnderkjenne.UgyldigTilstand(this::class, VurdertKlage.Bekreftet::class).left()
    }

    /** @return [IverksattKlage] */
    open fun iverksett(
        iverksattAttestering: Attestering.Iverksatt,
    ): Either<KunneIkkeIverksetteKlage.UgyldigTilstand, IverksattKlage> {
        return KunneIkkeIverksetteKlage.UgyldigTilstand(this::class, IverksattKlage::class).left()
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
            )
        }
    }
}

sealed class KunneIkkeBekrefteKlagesteg {
    object FantIkkeKlage : KunneIkkeBekrefteKlagesteg()
    data class UgyldigTilstand(val fra: KClass<out Klage>, val til: KClass<out Klage>) :
        KunneIkkeBekrefteKlagesteg()
}
