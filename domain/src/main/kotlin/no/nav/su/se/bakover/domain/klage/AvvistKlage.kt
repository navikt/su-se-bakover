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

interface AvvistKlageFelter : VilkårsvurdertKlageFelter {
    override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt
    val fritekstTilVedtaksbrev: String
}

/**
 * Representerer en klage når minst et av formkravene er besvart 'nei/false', og det har blitt lagret minst en gang
 * forrige-klasse: [VilkårsvurdertKlage.Bekreftet.Avvist]
 * neste-klasse: [KlageTilAttestering.Avvist]
 *
 * @param oppgaveId Må ha mulighet til å legge inn ny oppgaveId når man kommer fra attesteringssteget
 * @param attesteringer Må ha mulighet til å legge inn ny attestent når man kommer fra attesteringssteget
 */
data class AvvistKlage(
    private val forrigeSteg: VilkårsvurdertKlage.Bekreftet.Avvist,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    override val fritekstTilVedtaksbrev: String,
    override val oppgaveId: OppgaveId = forrigeSteg.oppgaveId,
    override val attesteringer: Attesteringshistorikk = forrigeSteg.attesteringer,
) : Klage, AvvistKlageFelter, KanLeggeTilFritekstTilAvvistBrev, VilkårsvurdertKlage.BekreftetFelter by forrigeSteg {

    override fun erÅpen() = true

    override fun bekreftVilkårsvurderinger(
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBekrefteKlagesteg.UgyldigTilstand, VilkårsvurdertKlage.Bekreftet.Avvist> {
        return VilkårsvurdertKlage.Bekreftet.Avvist(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            journalpostId = journalpostId,
            oppgaveId = oppgaveId,
            saksbehandler = saksbehandler,
            vilkårsvurderinger = vilkårsvurderinger,
            attesteringer = attesteringer,
            datoKlageMottatt = datoKlageMottatt,
            fritekstTilAvvistVedtaksbrev = fritekstTilVedtaksbrev,
        ).right()
    }

    override fun vilkårsvurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkårsvurderinger: VilkårsvurderingerTilKlage,
    ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
        return when (vilkårsvurderinger) {
            is VilkårsvurderingerTilKlage.Utfylt -> VilkårsvurdertKlage.Utfylt.create(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                datoKlageMottatt = datoKlageMottatt,
                vurderinger = null,
                klageinstanshendelser = Klageinstanshendelser.empty(),
                fritekstTilAvvistVedtaksbrev = fritekstTilVedtaksbrev,
            )
            is VilkårsvurderingerTilKlage.Påbegynt -> VilkårsvurdertKlage.Påbegynt(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderinger,
                attesteringer = attesteringer,
                datoKlageMottatt = datoKlageMottatt,
            )
        }.right()
    }

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

    override fun leggTilFritekstTilAvvistVedtaksbrev(
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekstTilAvvistVedtaksbrev: String,
    ): AvvistKlage {
        return AvvistKlage(
            forrigeSteg = forrigeSteg,
            saksbehandler = saksbehandler,
            fritekstTilVedtaksbrev = fritekstTilAvvistVedtaksbrev,
        )
    }

    override fun sendTilAttestering(
        saksbehandler: NavIdentBruker.Saksbehandler,
        opprettOppgave: () -> Either<KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave, OppgaveId>,
    ): Either<KunneIkkeSendeTilAttestering, KlageTilAttestering.Avvist> {
        return opprettOppgave().map { oppgaveId ->
            KlageTilAttestering.Avvist(
                forrigeSteg = this,
                oppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
            )
        }
    }

    override fun kanAvsluttes() = true

    override fun avslutt(
        saksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String,
        tidspunktAvsluttet: Tidspunkt,
    ) = AvsluttetKlage(
        underliggendeKlage = this,
        saksbehandler = saksbehandler,
        begrunnelse = begrunnelse,
        tidspunktAvsluttet = tidspunktAvsluttet,
    ).right()
}

sealed interface KunneIkkeLeggeTilFritekstForAvvist {
    object FantIkkeKlage : KunneIkkeLeggeTilFritekstForAvvist
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeLeggeTilFritekstForAvvist {
        val til = AvvistKlage::class
    }
}
