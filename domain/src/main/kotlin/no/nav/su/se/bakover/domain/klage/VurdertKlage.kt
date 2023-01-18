package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.getOrElse
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

interface VurdertKlageFelter : VilkårsvurdertKlageFelter {
    // Her ønsker vi å være mer spesifikke en super
    override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt
    val vurderinger: VurderingerTilKlage
    val klageinstanshendelser: Klageinstanshendelser
}

sealed interface VurdertKlage : Klage, VurdertKlageFelter {

    override fun getFritekstTilBrev(): Either<KunneIkkeHenteFritekstTilBrev.UgyldigTilstand, String> {
        return vurderinger.fritekstTilOversendelsesbrev.orEmpty().right()
    }

    override fun lagBrevRequest(
        hentNavnForNavIdent: (saksbehandler: NavIdentBruker.Saksbehandler) -> Either<KunneIkkeHenteNavnForNavIdent, String>,
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
            fritekst = this.vurderinger.fritekstTilOversendelsesbrev.orEmpty(),
            saksnummer = this.saksnummer,
            klageDato = this.datoKlageMottatt,
            vedtaksbrevDato = hentVedtaksbrevDato(this.id)
                ?: return KunneIkkeLageBrevRequestForKlage.FeilVedHentingAvVedtaksbrevDato.left(),
        ).right()
    }

    override fun vilkårsvurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkårsvurderinger: VilkårsvurderingerTilKlage,
    ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
        if (klageinstanshendelser.isNotEmpty() && vilkårsvurderinger.erAvvist()) {
            return KunneIkkeVilkårsvurdereKlage.KanIkkeAvviseEnKlageSomHarVærtOversendt.left()
        }
        return when (vilkårsvurderinger) {
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
                vurderinger = vurderinger,
                attesteringer = attesteringer,
                datoKlageMottatt = datoKlageMottatt,
                klageinstanshendelser = klageinstanshendelser,
                fritekstTilAvvistVedtaksbrev = null,
            )
        }.right()
    }

    override fun bekreftVilkårsvurderinger(
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBekrefteKlagesteg.UgyldigTilstand, VilkårsvurdertKlage.Bekreftet> {
        return VilkårsvurdertKlage.Bekreftet.TilVurdering(
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
        ).right()
    }

    data class Påbegynt(
        private val forrigeSteg: VilkårsvurdertKlage.Bekreftet.TilVurdering,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val vurderinger: VurderingerTilKlage.Påbegynt,
    ) : VurdertKlage, KlageSomKanVurderes, VilkårsvurdertKlage.Bekreftet.TilVurderingFelter by forrigeSteg {

        override fun vurder(
            saksbehandler: NavIdentBruker.Saksbehandler,
            vurderinger: VurderingerTilKlage,
        ): VurdertKlage {
            return when (vurderinger) {
                is VurderingerTilKlage.Påbegynt -> Påbegynt(
                    forrigeSteg = this.forrigeSteg,
                    saksbehandler = saksbehandler,
                    vurderinger = vurderinger,
                )
                is VurderingerTilKlage.Utfylt -> Utfylt(
                    forrigeSteg = this,
                    saksbehandler = saksbehandler,
                    vurderinger = vurderinger,
                )
            }
        }

        override fun erÅpen() = true

        override fun kanAvsluttes() = klageinstanshendelser.isEmpty()

        override fun avslutt(
            saksbehandler: NavIdentBruker.Saksbehandler,
            begrunnelse: String,
            tidspunktAvsluttet: Tidspunkt,
        ): Either<KunneIkkeAvslutteKlage.UgyldigTilstand, AvsluttetKlage> {
            return if (klageinstanshendelser.isEmpty()) {
                AvsluttetKlage(
                    underliggendeKlage = this,
                    saksbehandler = saksbehandler,
                    begrunnelse = begrunnelse,
                    tidspunktAvsluttet = tidspunktAvsluttet,
                ).right()
            } else {
                KunneIkkeAvslutteKlage.UgyldigTilstand(this::class).left()
            }
        }
    }

    interface UtfyltFelter : VurdertKlageFelter {
        override val vurderinger: VurderingerTilKlage.Utfylt

        fun erOpprettholdelse(): Boolean = vurderinger.vedtaksvurdering is VurderingerTilKlage.Vedtaksvurdering.Utfylt.Oppretthold
    }

    data class Utfylt(
        private val forrigeSteg: Påbegynt,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val vurderinger: VurderingerTilKlage.Utfylt,
    ) : VurdertKlage, UtfyltFelter, KlageSomKanVurderes, KanBekrefteKlagevurdering, VurdertKlageFelter by forrigeSteg {

        override fun vurder(
            saksbehandler: NavIdentBruker.Saksbehandler,
            vurderinger: VurderingerTilKlage,
        ): VurdertKlage {
            return when (vurderinger) {
                is VurderingerTilKlage.Påbegynt -> this.forrigeSteg.vurder(saksbehandler, vurderinger)
                is VurderingerTilKlage.Utfylt -> Utfylt(
                    forrigeSteg = this.forrigeSteg,
                    saksbehandler = saksbehandler,
                    vurderinger = vurderinger,
                )
            }
        }

        override fun erÅpen() = true

        override fun bekreftVurderinger(
            saksbehandler: NavIdentBruker.Saksbehandler,
        ): Bekreftet {
            return Bekreftet(
                forrigeSteg = this,
                saksbehandler = saksbehandler,
            )
        }

        override fun kanAvsluttes() = klageinstanshendelser.isEmpty()

        override fun avslutt(
            saksbehandler: NavIdentBruker.Saksbehandler,
            begrunnelse: String,
            tidspunktAvsluttet: Tidspunkt,
        ): Either<KunneIkkeAvslutteKlage.UgyldigTilstand, AvsluttetKlage> {
            return if (klageinstanshendelser.isEmpty()) {
                AvsluttetKlage(
                    underliggendeKlage = this,
                    saksbehandler = saksbehandler,
                    begrunnelse = begrunnelse,
                    tidspunktAvsluttet = tidspunktAvsluttet,
                ).right()
            } else {
                KunneIkkeAvslutteKlage.UgyldigTilstand(this::class).left()
            }
        }
    }

    /**
     * Denne tilstanden representerer klagen når:
     * 1) Den er i oppsummeringssteget i behandlingen.
     * 2) Når den har blitt underkjent.
     * 3) Når en klage har blitt sendt i retur fra klageinstansen.
     *
     * @param oppgaveId Vi oppretter en ny oppgave når:
     * 1) en [OversendtKlage] blir returnert fra klageinstansen.
     * 2) Når vi underkjenner en [KlageTilAttestering.Vurdert].
     * @param attesteringer Denne oppdateres ved underkjennelse
     * @param klageinstanshendelser Denne oppdateres ved retur fra klageinstans
     */
    data class Bekreftet(
        private val forrigeSteg: Utfylt,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val oppgaveId: OppgaveId = forrigeSteg.oppgaveId,
        override val attesteringer: Attesteringshistorikk = forrigeSteg.attesteringer,
        override val klageinstanshendelser: Klageinstanshendelser = forrigeSteg.klageinstanshendelser,
    ) : VurdertKlage, KlageSomKanVurderes, KanBekrefteKlagevurdering, UtfyltFelter by forrigeSteg {

        override fun erÅpen() = true

        override fun vurder(
            saksbehandler: NavIdentBruker.Saksbehandler,
            vurderinger: VurderingerTilKlage,
        ): VurdertKlage {
            return forrigeSteg.vurder(
                saksbehandler = saksbehandler,
                vurderinger = vurderinger,
            )
        }

        override fun bekreftVurderinger(
            saksbehandler: NavIdentBruker.Saksbehandler,
        ): Bekreftet {
            return this.copy(saksbehandler = saksbehandler)
        }

        override fun sendTilAttestering(
            saksbehandler: NavIdentBruker.Saksbehandler,
            opprettOppgave: () -> Either<KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave, OppgaveId>,
        ): Either<KunneIkkeSendeTilAttestering, KlageTilAttestering> {
            return opprettOppgave().map { oppgaveId ->
                KlageTilAttestering.Vurdert(
                    forrigeSteg = this,
                    oppgaveId = oppgaveId,
                    saksbehandler = saksbehandler,
                )
            }
        }

        override fun kanAvsluttes() = klageinstanshendelser.isEmpty()

        override fun avslutt(
            saksbehandler: NavIdentBruker.Saksbehandler,
            begrunnelse: String,
            tidspunktAvsluttet: Tidspunkt,
        ): Either<KunneIkkeAvslutteKlage.UgyldigTilstand, AvsluttetKlage> {
            return if (klageinstanshendelser.isEmpty()) {
                AvsluttetKlage(
                    underliggendeKlage = this,
                    saksbehandler = saksbehandler,
                    begrunnelse = begrunnelse,
                    tidspunktAvsluttet = tidspunktAvsluttet,
                ).right()
            } else {
                KunneIkkeAvslutteKlage.UgyldigTilstand(this::class).left()
            }
        }
    }
}

sealed interface KunneIkkeVurdereKlage {
    object FantIkkeKlage : KunneIkkeVurdereKlage
    object UgyldigOmgjøringsårsak : KunneIkkeVurdereKlage
    object UgyldigOmgjøringsutfall : KunneIkkeVurdereKlage
    object UgyldigOpprettholdelseshjemler : KunneIkkeVurdereKlage
    object KanIkkeVelgeBådeOmgjørOgOppretthold : KunneIkkeVurdereKlage
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeVurdereKlage {
        val til = VurdertKlage::class
    }
}
