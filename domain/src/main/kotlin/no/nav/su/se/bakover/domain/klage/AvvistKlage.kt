package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHenteNavnForNavIdent
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Representerer en klage når minst et av formkravene er besvart 'nei/false', og det har blitt lagret minst en gang
 * forrige-klasse: [VilkårsvurdertKlage.Bekreftet.Avvist]
 * neste-klasse: [KlageTilAttestering.Avvist]
 */
data class AvvistKlage private constructor(
    private val forrigeSteg: VilkårsvurdertKlage.Bekreftet.Avvist,
    val fritekstTilBrev: String,
) : Klage, VilkårsvurdertKlage by forrigeSteg {
    override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt = forrigeSteg.vilkårsvurderinger

    override fun getFritekstTilBrev(): Either<KunneIkkeHenteFritekstTilBrev.UgyldigTilstand, String> {
        return fritekstTilBrev.right()
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
            fritekst = this.fritekstTilBrev,
            saksnummer = this.saksnummer,
        ).right()
    }

    override fun vurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vurderinger: VurderingerTilKlage,
    ): Either<KunneIkkeVurdereKlage.UgyldigTilstand, VurdertKlage> {
        return KunneIkkeVurdereKlage.UgyldigTilstand(this::class, VurdertKlage::class).left()
    }

    override fun bekreftVurderinger(saksbehandler: NavIdentBruker.Saksbehandler): Either<KunneIkkeBekrefteKlagesteg.UgyldigTilstand, VurdertKlage.Bekreftet> {
        return KunneIkkeBekrefteKlagesteg.UgyldigTilstand(this::class, VurdertKlage.Bekreftet::class).left()
    }

    override fun leggTilAvvistFritekstTilBrev(
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekst: String,
    ): Either<KunneIkkeLeggeTilFritekstForAvvist.UgyldigTilstand, AvvistKlage> {
        return create(
            forrigeSteg = forrigeSteg.copy(saksbehandler = saksbehandler),
            fritekstTilBrev = fritekst,
        ).right()
    }

    override fun sendTilAttestering(
        saksbehandler: NavIdentBruker.Saksbehandler,
        opprettOppgave: () -> Either<KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave, OppgaveId>,
    ): Either<KunneIkkeSendeTilAttestering, KlageTilAttestering.Avvist> {
        return opprettOppgave().map { oppgaveId ->
            KlageTilAttestering.Avvist.create(
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
                fritekstTilBrev = fritekstTilBrev,
            )
        }
    }

    override fun oversend(iverksattAttestering: Attestering.Iverksatt): Either<KunneIkkeOversendeKlage, OversendtKlage> {
        return KunneIkkeOversendeKlage.UgyldigTilstand(this::class, OversendtKlage::class).left()
    }

    companion object {
        fun create(
            forrigeSteg: VilkårsvurdertKlage.Bekreftet.Avvist,
            fritekstTilBrev: String,
        ): AvvistKlage {
            return AvvistKlage(
                forrigeSteg, fritekstTilBrev,
            )
        }
    }
}

sealed class KunneIkkeLeggeTilFritekstForAvvist {
    object FantIkkeKlage : KunneIkkeLeggeTilFritekstForAvvist()
    data class UgyldigTilstand(val fra: KClass<out Klage>, val til: KClass<out Klage>) :
        KunneIkkeLeggeTilFritekstForAvvist()
}
