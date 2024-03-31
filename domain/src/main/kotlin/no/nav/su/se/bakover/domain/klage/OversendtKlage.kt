package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import behandling.klage.domain.KlageId
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand
import java.time.LocalDate
import kotlin.reflect.KClass

data class OversendtKlage(
    private val forrigeSteg: KlageTilAttestering.Vurdert,
    override val klageinstanshendelser: Klageinstanshendelser,
    override val attesteringer: Attesteringshistorikk,
) : Klage, VurdertKlage.UtfyltFelter by forrigeSteg {

    /**
     * Merk at i et større perspektiv, f.eks. fra klageinstansen (KA) eller statistikk, vil denne anses som åpen/ikke ferdigbehandlet.
     * Men for saksbehandlerene i førsteinstansen vil den bli ansett som ferdigbehandlet inntil den eventuelt kommer i retur av forskjellige årsaker.
     */
    override fun erÅpen() = false

    override fun getFritekstTilBrev(): Either<KunneIkkeHenteFritekstTilBrev.UgyldigTilstand, String> {
        return vurderinger.fritekstTilOversendelsesbrev.right()
    }

    fun genererOversendelsesbrev(
        // TODO jah: Hadde vært fint å sluppet IO i det laget her. Kan vi flytte til en funksjonell service-funksjon?
        hentVedtaksbrevDato: (klageId: KlageId) -> LocalDate?,
    ): Either<KunneIkkeLageBrevKommandoForKlage, KlageDokumentCommand> {
        return KlageDokumentCommand.Oppretthold(
            fødselsnummer = this.fnr,
            saksnummer = this.saksnummer,
            saksbehandler = this.saksbehandler,
            attestant = this.attesteringer.hentSisteAttestering().attestant,
            fritekst = this.vurderinger.fritekstTilOversendelsesbrev,
            klageDato = this.datoKlageMottatt,
            vedtaksbrevDato = hentVedtaksbrevDato(this.id)
                ?: return KunneIkkeLageBrevKommandoForKlage.FeilVedHentingAvVedtaksbrevDato.left(),
        ).right()
    }

    override fun kanAvsluttes() = false
    override fun avslutt(
        saksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String,
        tidspunktAvsluttet: Tidspunkt,
    ) = KunneIkkeAvslutteKlage.UgyldigTilstand(this::class).left()

    fun leggTilNyKlageinstanshendelse(
        tolketKlageinstanshendelse: TolketKlageinstanshendelse,
        lagOppgaveCallback: () -> Either<Klage.KunneIkkeLeggeTilNyKlageinstansHendelse, OppgaveId>,
    ): Either<Klage.KunneIkkeLeggeTilNyKlageinstansHendelse, Klage> {
        return lagOppgaveCallback().map { oppgaveId ->
            val oppdatertKlageinstanshendelser =
                this.klageinstanshendelser.leggTilNyttVedtak(tolketKlageinstanshendelse.tilProsessert(oppgaveId))

            when (tolketKlageinstanshendelse.utfall) {
                KlageinstansUtfall.TRUKKET,
                KlageinstansUtfall.OPPHEVET,
                KlageinstansUtfall.MEDHOLD,
                KlageinstansUtfall.DELVIS_MEDHOLD,
                KlageinstansUtfall.STADFESTELSE,
                KlageinstansUtfall.UGUNST,
                KlageinstansUtfall.AVVIST,
                -> this.copy(klageinstanshendelser = oppdatertKlageinstanshendelser)

                KlageinstansUtfall.RETUR -> this.forrigeSteg.returFraKlageinstans(
                    oppgaveId = oppgaveId,
                    klageinstanshendelser = oppdatertKlageinstanshendelser,
                )
            }
        }
    }
}

sealed interface KunneIkkeOversendeKlage {
    data object FantIkkeKlage : KunneIkkeOversendeKlage
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeOversendeKlage {
        val til = OversendtKlage::class
    }

    data object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeOversendeKlage
    data object FantIkkeJournalpostIdKnyttetTilVedtaket : KunneIkkeOversendeKlage
    data object KunneIkkeOversendeTilKlageinstans : KunneIkkeOversendeKlage
    data class KunneIkkeLageBrevRequest(
        val feil: KunneIkkeLageBrevKommandoForKlage,
    ) : KunneIkkeOversendeKlage

    data class KunneIkkeLageDokument(
        val feil: dokument.domain.KunneIkkeLageDokument,
    ) : KunneIkkeOversendeKlage
}
