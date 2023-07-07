package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.Person
import java.lang.IllegalStateException
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

interface KlageTilAttesteringFelter : VilkårsvurdertKlageFelter {
    override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt
}

sealed interface KlageTilAttestering : Klage, KlageTilAttesteringFelter, KanGenerereBrevutkast {

    data class Avvist(
        private val forrigeSteg: AvvistKlage,
        override val oppgaveId: OppgaveId,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
    ) : KlageTilAttestering, AvvistKlageFelter by forrigeSteg {

        override fun erÅpen() = true

        override fun getFritekstTilBrev(): Either<KunneIkkeHenteFritekstTilBrev.UgyldigTilstand, String> {
            return fritekstTilVedtaksbrev.right()
        }

        /**
         * @param hentVedtaksbrevDato brukes ikke for [Avvist]
         */
        override fun lagBrevRequest(
            utførtAv: NavIdentBruker,
            hentNavnForNavIdent: (saksbehandler: NavIdentBruker) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
            hentVedtaksbrevDato: (klageId: UUID) -> LocalDate?,
            hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
            clock: Clock,
        ): Either<KunneIkkeLageBrevRequestForKlage, LagBrevRequest.Klage> {
            utførtAv as NavIdentBruker.Attestant
            return genererAvvistVedtaksbrev(utførtAv, hentNavnForNavIdent, hentPerson, clock)
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
        /**
         * @throws IllegalStateException - dersom saksbehandler ikke har lagt til fritekst enda.
         */
        override val fritekstTilVedtaksbrev get() = getFritekstTilBrev().getOrElse {
            throw IllegalStateException("Vi har ikke fått lagret fritekst for klage $id")
        }

        override fun erÅpen() = true

        override fun getFritekstTilBrev() = vurderinger.fritekstTilOversendelsesbrev.right()

        /**
         * @param attestant kaster IllegalArgumentException dersom denne er null.
         */
        override fun lagBrevRequest(
            utførtAv: NavIdentBruker,
            hentNavnForNavIdent: (saksbehandler: NavIdentBruker) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
            hentVedtaksbrevDato: (klageId: UUID) -> LocalDate?,
            hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
            clock: Clock,
        ): Either<KunneIkkeLageBrevRequestForKlage, LagBrevRequest.Klage> {
            utførtAv as NavIdentBruker.Attestant
            return genererOversendelsesBrev(utførtAv, hentNavnForNavIdent, hentVedtaksbrevDato, hentPerson, clock)
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
