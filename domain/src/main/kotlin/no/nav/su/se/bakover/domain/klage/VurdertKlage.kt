package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.klage.domain.FormkravTilKlage
import behandling.klage.domain.KlageId
import behandling.klage.domain.VilkårsvurdertKlageFelter
import behandling.klage.domain.VurderingerTilKlage
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.brev.command.KlageDokumentCommand
import java.time.LocalDate
import kotlin.reflect.KClass

interface VurdertKlageFelter : VilkårsvurdertKlageFelter {
    // Her ønsker vi å være mer spesifikke en super
    override val vilkårsvurderinger: FormkravTilKlage.Utfylt
    val vurderinger: VurderingerTilKlage
    val klageinstanshendelser: Klageinstanshendelser
}

sealed interface VurdertKlage :
    Klage,
    VurdertKlageFelter,
    KanGenerereBrevutkast {
    /**
     * @throws IllegalStateException - dersom saksbehandler ikke har lagt til fritekst enda.
     */
    override val fritekstTilVedtaksbrev
        get() = getFritekstTilBrev().getOrElse {
            throw IllegalStateException("Vi har ikke fått lagret fritekst for klage $id")
        }

    val fritekstTilBrev: String?
        get() =
            when (val vurderinger = vurderinger) {
                is VurderingerTilKlage.Påbegynt -> vurderinger.fritekstTilOversendelsesbrev
                is VurderingerTilKlage.UtfyltOmgjøring -> null
                is VurderingerTilKlage.UtfyltOppretthold -> vurderinger.fritekstTilOversendelsesbrev
            }

    override fun getFritekstTilBrev(): Either<KunneIkkeHenteFritekstTilBrev.UgyldigTilstand, String> {
        return when (val vurderinger = vurderinger) {
            is VurderingerTilKlage.Påbegynt -> KunneIkkeHenteFritekstTilBrev.UgyldigTilstand(this::class).left()
            is VurderingerTilKlage.UtfyltOmgjøring -> KunneIkkeHenteFritekstTilBrev.UgyldigTilstand(this::class).left()
            is VurderingerTilKlage.UtfyltOppretthold -> vurderinger.fritekstTilOversendelsesbrev.right()
        }
    }

    /**
     * @param utførtAv brukes kun i attesteringsstegene
     */
    override fun lagBrevRequest(
        utførtAv: NavIdentBruker,
        hentVedtaksbrevDato: (klageId: KlageId) -> LocalDate?,
    ): Either<KunneIkkeLageBrevKommandoForKlage, KlageDokumentCommand> {
        return genererOversendelsesBrev(
            attestant = null,
            hentVedtaksbrevDato = hentVedtaksbrevDato,
        )
    }

    override fun vilkårsvurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkårsvurderinger: FormkravTilKlage,
    ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
        if (klageinstanshendelser.isNotEmpty() && vilkårsvurderinger.erAvvist()) {
            return KunneIkkeVilkårsvurdereKlage.KanIkkeAvviseEnKlageSomHarVærtOversendt.left()
        }
        return when (vilkårsvurderinger) {
            is FormkravTilKlage.Påbegynt -> VilkårsvurdertKlage.Påbegynt(
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
                sakstype = sakstype,
            )

            is FormkravTilKlage.Utfylt -> VilkårsvurdertKlage.Utfylt.create(
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
                sakstype = sakstype,
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
            sakstype = sakstype,
        ).right()
    }

    data class Påbegynt(
        private val forrigeSteg: VilkårsvurdertKlage.Bekreftet.TilVurdering,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val vurderinger: VurderingerTilKlage.Påbegynt,
        override val sakstype: Sakstype,
    ) : VurdertKlage,
        KlageSomKanVurderes,
        VilkårsvurdertKlage.Bekreftet.TilVurderingFelter by forrigeSteg {

        override fun vurder(
            saksbehandler: NavIdentBruker.Saksbehandler,
            vurderinger: VurderingerTilKlage,
        ): VurdertKlage {
            return when (vurderinger) {
                is VurderingerTilKlage.Påbegynt -> Påbegynt(
                    forrigeSteg = this.forrigeSteg,
                    saksbehandler = saksbehandler,
                    vurderinger = vurderinger,
                    sakstype = sakstype,
                )

                is VurderingerTilKlage.Utfylt -> Utfylt(
                    forrigeSteg = this,
                    saksbehandler = saksbehandler,
                    vurderinger = vurderinger,
                    sakstype = sakstype,
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
                    avsluttetTidspunkt = tidspunktAvsluttet,
                ).right()
            } else {
                KunneIkkeAvslutteKlage.UgyldigTilstand(this::class).left()
            }
        }
    }

    interface UtfyltFelter : VurdertKlageFelter {
        override val vurderinger: VurderingerTilKlage.Utfylt

        fun erOpprettholdelse(): Boolean =
            vurderinger.vedtaksvurdering is VurderingerTilKlage.Vedtaksvurdering.Utfylt.Oppretthold
    }

    // TODO: vurder å ha egen for omgjøringferdigstill for vedtaksvurdering omgjøring garanti på typer
    data class Utfylt(
        private val forrigeSteg: Påbegynt,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val vurderinger: VurderingerTilKlage.Utfylt,
        override val sakstype: Sakstype,
    ) : VurdertKlage,
        UtfyltFelter,
        KlageSomKanVurderes,
        KanBekrefteKlagevurdering,
        VurdertKlageFelter by forrigeSteg {

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
                    sakstype = sakstype,
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
                sakstype = sakstype,
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
                    avsluttetTidspunkt = tidspunktAvsluttet,
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
        override val sakstype: Sakstype,
    ) : VurdertKlage,
        KlageSomKanVurderes,
        KanBekrefteKlagevurdering,
        UtfyltFelter by forrigeSteg {

        override fun ferdigstillOmgjøring(
            saksbehandler: NavIdentBruker.Saksbehandler,
            klage: Bekreftet,
            ferdigstiltTidspunkt: Tidspunkt,
        ): Either<KunneIkkeFerdigstilleOmgjøringsKlage, FerdigstiltOmgjortKlage> {
            return FerdigstiltOmgjortKlage(
                forrigeSteg = forrigeSteg,
                saksbehandler = saksbehandler,
                sakstype = sakstype,
                klageinstanshendelser = klage.klageinstanshendelser,
                datoklageferdigstilt = ferdigstiltTidspunkt,
            ).right()
        }

        override fun erÅpen() = true

        // Hvis det er en vurdertKlage så sjekker man på "forrigeSteg" og den vil vel alltid være påbegynt ergo langt fra utfylt.
        // TODO: kanskje man skal se om det har kommet inn nye vurderinger i stedet for?
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
        ): Either<KunneIkkeSendeKlageTilAttestering, KlageTilAttestering> {
            return KlageTilAttestering.Vurdert(
                forrigeSteg = this,
                saksbehandler = saksbehandler,
                sakstype = sakstype,
            ).right()
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
                    avsluttetTidspunkt = tidspunktAvsluttet,
                ).right()
            } else {
                KunneIkkeAvslutteKlage.UgyldigTilstand(this::class).left()
            }
        }
    }
}

sealed interface KunneIkkeVurdereKlage {
    data object FantIkkeKlage : KunneIkkeVurdereKlage
    data object UgyldigOmgjøringsårsak : KunneIkkeVurdereKlage
    data object UgyldigOpprettholdelseshjemler : KunneIkkeVurdereKlage
    data object KanIkkeVelgeBådeOmgjørOgOppretthold : KunneIkkeVurdereKlage
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeVurdereKlage {
        val til = VurdertKlage::class
    }
}
