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

data class OversendtKlage private constructor(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val sakId: UUID,
    override val saksnummer: Saksnummer,
    override val fnr: Fnr,
    override val journalpostId: JournalpostId,
    override val oppgaveId: OppgaveId,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val datoKlageMottatt: LocalDate,
    val klageinstanshendelser: Klageinstanshendelser,
    val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
    val vurderinger: VurderingerTilKlage.Utfylt,
    val attesteringer: Attesteringshistorikk,
) : Klage {

    override fun erÅpen() = false

    override fun getFritekstTilBrev(): Either<KunneIkkeHenteFritekstTilBrev.UgyldigTilstand, String> {
        return vurderinger.fritekstTilBrev.right()
    }

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
            fritekst = this.vurderinger.fritekstTilBrev,
            saksnummer = this.saksnummer,
            klageDato = this.datoKlageMottatt,
            vedtakDato = hentVedtakDato(this.id)
                ?: return KunneIkkeLageBrevRequest.FeilVedHentingAvVedtakDato.left(),
        ).right()
    }

    override fun kanAvsluttes() = false
    override fun avslutt(
        saksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String,
        tidspunktAvsluttet: Tidspunkt,
    ) = KunneIkkeAvslutteKlage.UgyldigTilstand(this::class).left()

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
            vurderinger: VurderingerTilKlage.Utfylt,
            attesteringer: Attesteringshistorikk,
            datoKlageMottatt: LocalDate,
            klageinstanshendelser: Klageinstanshendelser,
        ): OversendtKlage {
            return OversendtKlage(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderinger,
                vurderinger = vurderinger,
                attesteringer = attesteringer,
                datoKlageMottatt = datoKlageMottatt,
                klageinstanshendelser = klageinstanshendelser,
            )
        }
    }

    override fun leggTilNyKlageinstanshendelse(
        tolketKlageinstanshendelse: TolketKlageinstanshendelse,
        lagOppgaveCallback: () -> Either<Klage.KunneIkkeLeggeTilNyKlageinstansHendelse, OppgaveId>,
    ): Either<Klage.KunneIkkeLeggeTilNyKlageinstansHendelse, Klage> {
        return lagOppgaveCallback().map { oppgaveId ->
            val oppdatertKlage = leggTilProsessertKlageinstanshendelse(tolketKlageinstanshendelse.tilProsessert(oppgaveId))

            when (tolketKlageinstanshendelse.utfall) {
                KlageinstansUtfall.TRUKKET,
                KlageinstansUtfall.OPPHEVET,
                KlageinstansUtfall.MEDHOLD,
                KlageinstansUtfall.DELVIS_MEDHOLD,
                KlageinstansUtfall.STADFESTELSE,
                KlageinstansUtfall.UGUNST,
                KlageinstansUtfall.AVVIST,
                -> oppdatertKlage
                KlageinstansUtfall.RETUR -> oppdatertKlage.toBekreftet(oppgaveId)
            }
        }
    }

    private fun toBekreftet(oppgaveId: OppgaveId) =
        VurdertKlage.Bekreftet.create(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            journalpostId = journalpostId,
            oppgaveId = oppgaveId,
            saksbehandler = saksbehandler,
            vilkårsvurderinger = vilkårsvurderinger,
            vurderinger = vurderinger,
            attesteringer = attesteringer,
            datoKlageMottatt = datoKlageMottatt,
            klageinstanshendelser = klageinstanshendelser,
        )

    private fun leggTilProsessertKlageinstanshendelse(hendelse: ProsessertKlageinstanshendelse): OversendtKlage =
        this.copy(klageinstanshendelser = this.klageinstanshendelser.leggTilNyttVedtak(hendelse))
}

sealed class KunneIkkeOversendeKlage {
    object FantIkkeKlage : KunneIkkeOversendeKlage()
    data class UgyldigTilstand(val fra: KClass<out Klage>, val til: KClass<out Klage>) :
        KunneIkkeOversendeKlage()

    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeOversendeKlage()
    data class KunneIkkeLageBrev(val feil: KunneIkkeLageBrevForKlage) : KunneIkkeOversendeKlage()
    object FantIkkeJournalpostIdKnyttetTilVedtaket : KunneIkkeOversendeKlage()
    object KunneIkkeOversendeTilKlageinstans : KunneIkkeOversendeKlage()
    data class KunneIkkeLageBrevRequest(val feil: no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevRequest) :
        KunneIkkeOversendeKlage()
}
