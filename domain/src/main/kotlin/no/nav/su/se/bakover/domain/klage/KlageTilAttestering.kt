package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.Person
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

interface KlageTilAttesteringFelter : VilkårsvurdertKlageFelter {
    override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt
}

sealed interface KlageTilAttestering : Klage, KlageTilAttesteringFelter {

    data class Avvist(
        private val forrigeSteg: AvvistKlage,
        override val oppgaveId: OppgaveId,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
    ) : KlageTilAttestering, AvvistKlageFelter by forrigeSteg {

        override fun erÅpen() = true

        override fun getFritekstTilBrev(): Either<KunneIkkeHenteFritekstTilBrev.UgyldigTilstand, String> {
            return fritekstTilVedtaksbrev.right()
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
                fritekst = this.fritekstTilVedtaksbrev,
                saksnummer = this.saksnummer,
            ).right()
        }

        fun iverksett(
            iverksattAttestering: Attestering.Iverksatt,
        ): Either<KunneIkkeIverksetteAvvistKlage, IverksattAvvistKlage> {
            if (iverksattAttestering.attestant.navIdent == saksbehandler.navIdent) {
                return KunneIkkeIverksetteAvvistKlage.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
            return IverksattAvvistKlage(
                forrigeSteg = this,
                attesteringer = attesteringer.leggTilNyAttestering(iverksattAttestering),
            ).right()
        }

        override fun underkjenn(
            underkjentAttestering: Attestering.Underkjent,
            opprettOppgave: () -> Either<KunneIkkeUnderkjenne.KunneIkkeOppretteOppgave, OppgaveId>,
        ): Either<KunneIkkeUnderkjenne, AvvistKlage> {
            if (underkjentAttestering.attestant.navIdent == saksbehandler.navIdent) {
                return KunneIkkeUnderkjenne.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
            return opprettOppgave().map { oppgaveId ->
                // I dette tilfellet gir det mening å bare legge til manglende parametre på forrige steg, da vi bare skal ett steg tilbake.
                forrigeSteg.copy(
                    oppgaveId = oppgaveId,
                    attesteringer = attesteringer.leggTilNyAttestering(underkjentAttestering),
                )
            }
        }

        override fun kanAvsluttes() = false

        override fun avslutt(
            saksbehandler: NavIdentBruker.Saksbehandler,
            begrunnelse: String,
            tidspunktAvsluttet: Tidspunkt,
        ) = KunneIkkeAvslutteKlage.UgyldigTilstand(this::class).left()
    }

    data class Vurdert(
        private val forrigeSteg: VurdertKlage.Bekreftet,
        override val oppgaveId: OppgaveId,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
    ) : KlageTilAttestering, VurdertKlage.UtfyltFelter by forrigeSteg {

        override fun erÅpen() = true

        override fun getFritekstTilBrev() = vurderinger.fritekstTilOversendelsesbrev.right()

        override fun lagBrevRequest(
            hentNavnForNavIdent: (saksbehandler: NavIdentBruker.Saksbehandler) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
            hentVedtakDato: (klageId: UUID) -> LocalDate?,
            hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
            clock: Clock,
        ): Either<KunneIkkeLageBrevRequest, LagBrevRequest.Klage> {
            return LagBrevRequest.Klage.Oppretthold(
                person = hentPerson(this.fnr).getOrHandle {
                    return KunneIkkeLageBrevRequest.FeilVedHentingAvPerson(it).left()
                },
                dagensDato = LocalDate.now(clock),
                saksbehandlerNavn = hentNavnForNavIdent(this.saksbehandler).getOrHandle {
                    return KunneIkkeLageBrevRequest.FeilVedHentingAvSaksbehandlernavn(it).left()
                },
                fritekst = this.vurderinger.fritekstTilOversendelsesbrev,
                saksnummer = this.saksnummer,
                klageDato = this.datoKlageMottatt,
                vedtakDato = hentVedtakDato(this.id)
                    ?: return KunneIkkeLageBrevRequest.FeilVedHentingAvVedtakDato.left(),
            ).right()
        }

        override fun underkjenn(
            underkjentAttestering: Attestering.Underkjent,
            opprettOppgave: () -> Either<KunneIkkeUnderkjenne.KunneIkkeOppretteOppgave, OppgaveId>,
        ): Either<KunneIkkeUnderkjenne, VurdertKlage.Bekreftet> {
            if (underkjentAttestering.attestant.navIdent == saksbehandler.navIdent) {
                return KunneIkkeUnderkjenne.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
            return opprettOppgave().map { oppgaveId ->
                // I dette tilfellet gir det mening å bare legge til manglende parametre på forrige steg, da vi bare skal ett steg tilbake.
                this.forrigeSteg.copy(
                    oppgaveId = oppgaveId,
                    attesteringer = attesteringer.leggTilNyAttestering(underkjentAttestering),
                )
            }
        }

        fun oversend(
            iverksattAttestering: Attestering.Iverksatt,
        ): Either<KunneIkkeOversendeKlage, OversendtKlage> {
            if (iverksattAttestering.attestant.navIdent == saksbehandler.navIdent) {
                return KunneIkkeOversendeKlage.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
            return OversendtKlage(
                forrigeSteg = this,
                attesteringer = attesteringer.leggTilNyAttestering(iverksattAttestering),
                klageinstanshendelser = klageinstanshendelser,
            ).right()
        }

        override fun kanAvsluttes() = false
        override fun avslutt(
            saksbehandler: NavIdentBruker.Saksbehandler,
            begrunnelse: String,
            tidspunktAvsluttet: Tidspunkt,
        ) = KunneIkkeAvslutteKlage.UgyldigTilstand(this::class).left()

        fun returFraKlageinstans(
            oppgaveId: OppgaveId,
            klageinstanshendelser: Klageinstanshendelser,
        ): VurdertKlage.Bekreftet {
            // I dette tilfellet gir det mening å bare legge til manglende parametre på forrige steg, da vi bare skal ett steg tilbake.
            return forrigeSteg.copy(
                oppgaveId = oppgaveId,
                klageinstanshendelser = klageinstanshendelser,
            )
        }
    }
}

sealed interface KunneIkkeSendeTilAttestering {
    object FantIkkeKlage : KunneIkkeSendeTilAttestering
    object KunneIkkeOppretteOppgave : KunneIkkeSendeTilAttestering
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeSendeTilAttestering {
        val til = KlageTilAttestering::class
    }
}

sealed interface KunneIkkeUnderkjenne {
    object FantIkkeKlage : KunneIkkeUnderkjenne
    object KunneIkkeOppretteOppgave : KunneIkkeUnderkjenne
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeUnderkjenne
    data class UgyldigTilstand(val fra: KClass<out Klage>, val til: KClass<out Klage>) : KunneIkkeUnderkjenne
}
