package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
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

    fun genererOversendelsesbrev(
        hentNavnForNavIdent: (saksbehandler: NavIdentBruker) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
        hentVedtaksbrevDato: (klageId: UUID) -> LocalDate?,
        hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
        clock: Clock,
    ): Either<KunneIkkeLageBrevRequestForKlage, LagBrevRequest.Klage> {
        return LagBrevRequest.Klage.Oppretthold(
            person = hentPerson(this.fnr).getOrElse {
                return KunneIkkeLageBrevRequestForKlage.FeilVedHentingAvPerson(it).left()
            },
            dagensDato = LocalDate.now(clock),
            saksbehandlerNavn = hentNavnForNavIdent(this.saksbehandler).getOrElse {
                return KunneIkkeLageBrevRequestForKlage.FeilVedHentingAvSaksbehandlernavn(it).left()
            },
            attestantNavn = this.attesteringer.hentSisteAttestering().attestant.let { hentNavnForNavIdent(it) }
                .getOrElse { return KunneIkkeLageBrevRequestForKlage.FeilVedHentingAvAttestantnavn(it).left() },
            fritekst = this.vurderinger.fritekstTilOversendelsesbrev,
            saksnummer = this.saksnummer,
            klageDato = this.datoKlageMottatt,
            vedtaksbrevDato = hentVedtaksbrevDato(this.id)
                ?: return KunneIkkeLageBrevRequestForKlage.FeilVedHentingAvVedtaksbrevDato.left(),
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
        val feil: KunneIkkeLageBrevRequestForKlage,
    ) : KunneIkkeOversendeKlage
}
