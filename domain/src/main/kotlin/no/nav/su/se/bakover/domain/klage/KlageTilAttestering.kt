package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.klage.domain.FormkravTilKlage
import behandling.klage.domain.KlageId
import behandling.klage.domain.VilkårsvurdertKlageFelter
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand
import java.time.LocalDate
import kotlin.reflect.KClass

interface KlageTilAttesteringFelter : VilkårsvurdertKlageFelter {
    override val vilkårsvurderinger: FormkravTilKlage.Utfylt
}

sealed interface KlageTilAttestering :
    Klage,
    KlageTilAttesteringFelter,
    KanGenerereBrevutkast {

    data class Avvist(
        private val forrigeSteg: AvvistKlage,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val sakstype: Sakstype,
    ) : KlageTilAttestering,
        AvvistKlageFelter by forrigeSteg {

        override fun erÅpen() = true

        override fun getFritekstTilBrev(): Either<KunneIkkeHenteFritekstTilBrev.UgyldigTilstand, String> {
            return fritekstTilVedtaksbrev.right()
        }

        /**
         * @param utførtAv forventes at denne er en attestant
         * @param hentVedtaksbrevDato brukes ikke for [Avvist]
         */
        override fun lagBrevRequest(
            utførtAv: NavIdentBruker,
            hentVedtaksbrevDato: (klageId: KlageId) -> LocalDate?,
        ): Either<KunneIkkeLageBrevKommandoForKlage, KlageDokumentCommand> {
            utførtAv as NavIdentBruker.Attestant
            return lagAvvistVedtaksbrevKommando(attestant = utførtAv)
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
                sakstype = sakstype,
            ).right()
        }

        override fun underkjenn(
            underkjentAttestering: Attestering.Underkjent,
        ): Either<KunneIkkeUnderkjenneKlage, AvvistKlage> {
            if (underkjentAttestering.attestant.navIdent == saksbehandler.navIdent) {
                return KunneIkkeUnderkjenneKlage.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
            return forrigeSteg.copy(
                oppgaveId = oppgaveId,
                attesteringer = attesteringer.leggTilNyAttestering(underkjentAttestering),
            ).right()
        }

        override fun kanAvsluttes() = false

        override fun avslutt(
            saksbehandler: NavIdentBruker.Saksbehandler,
            begrunnelse: String,
            tidspunktAvsluttet: Tidspunkt,
        ) = KunneIkkeAvslutteKlage.UgyldigTilstand(this::class).left()
    }

    data class Vurdert(
        private val forrigeSteg: VurdertKlage.BekreftetOversendtTilKA,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val sakstype: Sakstype,
    ) : KlageTilAttestering,
        VurdertKlage.BekreftetOversendtTilKAFelter by forrigeSteg { // TODO: denne blir klønete

        /**
         * @throws IllegalStateException - dersom saksbehandler ikke har lagt til fritekst enda.
         */
        override val fritekstTilVedtaksbrev
            get() = getFritekstTilBrev().getOrElse {
                throw IllegalStateException("Vi har ikke fått lagret fritekst for klage $id")
            }

        override fun erÅpen() = true

        override fun getFritekstTilBrev(): Either<KunneIkkeHenteFritekstTilBrev.UgyldigTilstand, String> = vurderinger.fritekstTilOversendelsesbrev.right()

        /**
         * @param utførtAv forventes at denne er NavIdentBruker.Attestant
         */
        override fun lagBrevRequest(
            utførtAv: NavIdentBruker,
            hentVedtaksbrevDato: (klageId: KlageId) -> LocalDate?,
        ): Either<KunneIkkeLageBrevKommandoForKlage, KlageDokumentCommand> {
            utførtAv as NavIdentBruker.Attestant
            return genererOversendelsesBrev(
                attestant = utførtAv,
                hentVedtaksbrevDato = hentVedtaksbrevDato,
            )
        }

        override fun underkjenn(
            underkjentAttestering: Attestering.Underkjent,
        ): Either<KunneIkkeUnderkjenneKlage, VurdertKlage.BekreftetOversendtTilKA> {
            if (underkjentAttestering.attestant.navIdent == saksbehandler.navIdent) {
                return KunneIkkeUnderkjenneKlage.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }

            return when (val forrigeSteg = this.forrigeSteg) {
                is VurdertKlage.BekreftetDelvisOmgjøringKA -> forrigeSteg.copy(
                    oppgaveId = oppgaveId,
                    attesteringer = attesteringer.leggTilNyAttestering(underkjentAttestering),
                ).right()
                is VurdertKlage.BekreftetOpprettholdt -> forrigeSteg.copy(
                    oppgaveId = oppgaveId,
                    attesteringer = attesteringer.leggTilNyAttestering(underkjentAttestering),
                ).right()
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
                sakstype = sakstype,
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
        ): VurdertKlage.BekreftetOversendtTilKA {
            // I dette tilfellet gir det mening å bare legge til manglende parametre på forrige steg, da vi bare skal ett steg tilbake.
            return when (val temp = forrigeSteg) {
                is VurdertKlage.BekreftetDelvisOmgjøringKA -> temp.copy(
                    oppgaveId = oppgaveId,
                    klageinstanshendelser = klageinstanshendelser,
                )
                is VurdertKlage.BekreftetOpprettholdt -> temp.copy(
                    oppgaveId = oppgaveId,
                    klageinstanshendelser = klageinstanshendelser,
                )
            }
        }
    }
}

sealed interface KunneIkkeSendeKlageTilAttestering {
    data object FantIkkeKlage : KunneIkkeSendeKlageTilAttestering
    data object KunneIkkeOppretteOppgave : KunneIkkeSendeKlageTilAttestering
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeSendeKlageTilAttestering {
        val til = KlageTilAttestering::class
    }
}

sealed interface KunneIkkeUnderkjenneKlage {
    data object FantIkkeKlage : KunneIkkeUnderkjenneKlage
    data object KunneIkkeOppretteOppgave : KunneIkkeUnderkjenneKlage
    data object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeUnderkjenneKlage
    data class UgyldigTilstand(val fra: KClass<out Klage>, val til: KClass<out Klage>) : KunneIkkeUnderkjenneKlage
}
