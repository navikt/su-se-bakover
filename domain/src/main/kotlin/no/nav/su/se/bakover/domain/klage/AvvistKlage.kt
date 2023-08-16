package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand
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
) : Klage,
    AvvistKlageFelter,
    KanLeggeTilFritekstTilAvvistBrev,
    VilkårsvurdertKlage.BekreftetFelter by forrigeSteg,
    KanGenerereBrevutkast {

    /**
     * @param utførtAv brukes kun i attesteringsstegene
     * @param hentVedtaksbrevDato brukes ikke for [AvvistKlage]
     */
    override fun lagBrevRequest(
        utførtAv: NavIdentBruker,
        hentVedtaksbrevDato: (klageId: UUID) -> LocalDate?,
    ): Either<KunneIkkeLageBrevKommandoForKlage, KlageDokumentCommand> {
        return lagAvvistVedtaksbrevKommando(attestant = null)
    }

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
        opprettOppgave: () -> Either<KunneIkkeSendeKlageTilAttestering.KunneIkkeOppretteOppgave, OppgaveId>,
    ): Either<KunneIkkeSendeKlageTilAttestering, KlageTilAttestering.Avvist> {
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
        avsluttetTidspunkt = tidspunktAvsluttet,
    ).right()
}

sealed interface KunneIkkeLeggeTilFritekstForAvvist {
    data object FantIkkeKlage : KunneIkkeLeggeTilFritekstForAvvist
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeLeggeTilFritekstForAvvist {
        val til = AvvistKlage::class
    }
}
