package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.Person
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
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

    override fun lagBrevRequest(
        hentNavnForNavIdent: (saksbehandler: NavIdentBruker.Saksbehandler) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
        hentVedtaksbrevDato: (klageId: UUID) -> LocalDate?,
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
            vedtaksbrevDato = hentVedtaksbrevDato(this.id)
                ?: return KunneIkkeLageBrevRequest.FeilVedHentingAvVedtaksbrevDato.left(),
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
    object FantIkkeKlage : KunneIkkeOversendeKlage
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeOversendeKlage {
        val til = OversendtKlage::class
    }

    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeOversendeKlage
    data class KunneIkkeLageBrev(val feil: KunneIkkeLageBrevForKlage) : KunneIkkeOversendeKlage
    object FantIkkeJournalpostIdKnyttetTilVedtaket : KunneIkkeOversendeKlage
    object KunneIkkeOversendeTilKlageinstans : KunneIkkeOversendeKlage
    data class KunneIkkeLageBrevRequest(
        val feil: no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevRequest,
    ) : KunneIkkeOversendeKlage
}
