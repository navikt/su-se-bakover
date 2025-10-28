package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
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
import no.nav.su.se.bakover.domain.klage.VurdertKlage.Bekreftet.Companion.createBekreftet
import no.nav.su.se.bakover.domain.klage.VurdertKlage.Utfylt.Companion.createUtfylt
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
    VurdertKlageFelter {
    val fritekstTilBrev: String? // Spesifikt for å slippe å ha flere lagre metoder i db

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
        KanGenerereBrevutkast,
        KlageSomKanVurderes,
        VilkårsvurdertKlage.Bekreftet.TilVurderingFelter by forrigeSteg {

        override val fritekstTilBrev: String?
            get() = vurderinger.fritekstTilOversendelsesbrev

        override val fritekstTilVedtaksbrev: String?
            get() = vurderinger.fritekstTilOversendelsesbrev

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
                is VurderingerTilKlage.Utfylt -> createUtfylt(
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
                    saksbehandler = saksbehandler,
                    begrunnelse = begrunnelse,
                    avsluttetTidspunkt = tidspunktAvsluttet,
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    fnr = fnr,
                    journalpostId = journalpostId,
                    oppgaveId = oppgaveId,
                    datoKlageMottatt = datoKlageMottatt,
                    sakstype = sakstype,
                ).right()
            } else {
                KunneIkkeAvslutteKlage.UgyldigTilstand(this::class).left()
            }
        }
    }

    sealed interface Utfylt :
        VurdertKlageFelter,
        VurdertKlage,
        KlageSomKanVurderes,
        KanBekrefteKlagevurdering {
        override val vurderinger: VurderingerTilKlage.Utfylt
        override val saksbehandler: NavIdentBruker.Saksbehandler
        override val sakstype: Sakstype
        fun internalForrigeStegUtfylt(): Påbegynt

        companion object {
            fun createUtfylt(
                vurderinger: VurderingerTilKlage.Utfylt,
                forrigeSteg: Påbegynt,
                saksbehandler: NavIdentBruker.Saksbehandler,
                sakstype: Sakstype,
            ): Utfylt {
                return when (vurderinger) {
                    is VurderingerTilKlage.UtfyltOmgjøring -> UtfyltOmgjør.create(forrigeSteg, saksbehandler, vurderinger, sakstype)
                    is VurderingerTilKlage.UtfyltOppretthold -> UtfyltOppretthold.create(
                        forrigeSteg,
                        saksbehandler,
                        vurderinger,
                        sakstype,
                        fritekstTilVedtaksbrev = vurderinger.fritekstTilOversendelsesbrev,
                    )

                    is VurderingerTilKlage.UtfyltDelvisOmgjøringKA -> UtfyltDelvisOmgjøringKA.create(
                        forrigeSteg,
                        saksbehandler,
                        vurderinger,
                        sakstype,
                        fritekstTilVedtaksbrev = vurderinger.fritekstTilOversendelsesbrev,
                    )
                }
            }
        }

        override fun vurder(
            saksbehandler: NavIdentBruker.Saksbehandler,
            vurderinger: VurderingerTilKlage,
        ): VurdertKlage {
            val forrigeSteg = internalForrigeStegUtfylt()
            return when (vurderinger) {
                is VurderingerTilKlage.Påbegynt -> forrigeSteg.vurder(saksbehandler, vurderinger)
                is VurderingerTilKlage.Utfylt -> createUtfylt(
                    forrigeSteg = forrigeSteg,
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
            return createBekreftet(
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
                    saksbehandler = saksbehandler,
                    begrunnelse = begrunnelse,
                    avsluttetTidspunkt = tidspunktAvsluttet,
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    fnr = fnr,
                    journalpostId = journalpostId,
                    oppgaveId = oppgaveId,
                    datoKlageMottatt = datoKlageMottatt,
                    sakstype = sakstype,
                ).right()
            } else {
                KunneIkkeAvslutteKlage.UgyldigTilstand(this::class).left()
            }
        }
    }

    data class UtfyltOmgjør private constructor(
        private val forrigeSteg: Påbegynt,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val vurderinger: VurderingerTilKlage.UtfyltOmgjøring,
        override val sakstype: Sakstype,
    ) : Utfylt,
        VurdertKlageFelter by forrigeSteg {
        override fun internalForrigeStegUtfylt(): Påbegynt = forrigeSteg
        override val fritekstTilBrev: String?
            get() = null

        companion object {
            fun create(
                forrigeSteg: Påbegynt,
                saksbehandler: NavIdentBruker.Saksbehandler,
                vurderinger: VurderingerTilKlage.UtfyltOmgjøring,
                sakstype: Sakstype,
            ): UtfyltOmgjør {
                return UtfyltOmgjør(
                    forrigeSteg = forrigeSteg,
                    saksbehandler = saksbehandler,
                    vurderinger = vurderinger,
                    sakstype = sakstype,
                )
            }
        }
    }

    /*
        Merge klassene av denne? innholdet er helt likt, enten må vi ha en type eller en klasse
        -> må se om typen blir bevart på en annen måte ellers lukter det et klassebaserthierarki altså
        hmmm vurderinger har typen og kan skilles på slik og flyten skal være lik for disse to hele veien ut
        mao burde de kunne være samme klasse men lager to i første iterasjon og ser hvordan modelleringen blir.
        TODO: skal vi ha to flytter vi hele logikken inn i interfacet
     */
    sealed interface UtfyltOversendtTilKA : Utfylt
    data class UtfyltDelvisOmgjøringKA internal constructor(
        private val forrigeSteg: Påbegynt,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val vurderinger: VurderingerTilKlage.UtfyltDelvisOmgjøringKA,
        override val sakstype: Sakstype,
        override val fritekstTilVedtaksbrev: String,
    ) : KanGenerereBrevutkast,
        UtfyltOversendtTilKA,
        VurdertKlageFelter by forrigeSteg {
        override fun internalForrigeStegUtfylt(): Påbegynt = forrigeSteg

        companion object {
            fun create(
                forrigeSteg: Påbegynt,
                saksbehandler: NavIdentBruker.Saksbehandler,
                vurderinger: VurderingerTilKlage.UtfyltDelvisOmgjøringKA,
                sakstype: Sakstype,
                fritekstTilVedtaksbrev: String,
            ): UtfyltDelvisOmgjøringKA {
                return UtfyltDelvisOmgjøringKA(
                    forrigeSteg = forrigeSteg,
                    saksbehandler = saksbehandler,
                    vurderinger = vurderinger,
                    sakstype = sakstype,
                    fritekstTilVedtaksbrev = fritekstTilVedtaksbrev,
                )
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

        override val fritekstTilBrev: String
            get() = vurderinger.fritekstTilOversendelsesbrev
    }

    data class UtfyltOppretthold internal constructor(
        private val forrigeSteg: Påbegynt,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val vurderinger: VurderingerTilKlage.UtfyltOppretthold,
        override val sakstype: Sakstype,
        override val fritekstTilVedtaksbrev: String,
    ) : KanGenerereBrevutkast,
        UtfyltOversendtTilKA,
        VurdertKlageFelter by forrigeSteg {
        override fun internalForrigeStegUtfylt(): Påbegynt = forrigeSteg

        companion object {
            fun create(
                forrigeSteg: Påbegynt,
                saksbehandler: NavIdentBruker.Saksbehandler,
                vurderinger: VurderingerTilKlage.UtfyltOppretthold,
                sakstype: Sakstype,
                fritekstTilVedtaksbrev: String,
            ): UtfyltOppretthold {
                return UtfyltOppretthold(
                    forrigeSteg = forrigeSteg,
                    saksbehandler = saksbehandler,
                    vurderinger = vurderinger,
                    sakstype = sakstype,
                    fritekstTilVedtaksbrev = fritekstTilVedtaksbrev,
                )
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

        override val fritekstTilBrev: String
            get() = vurderinger.fritekstTilOversendelsesbrev
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
    sealed interface Bekreftet :
        VurdertKlage,
        KlageSomKanVurderes,
        KanBekrefteKlagevurdering {
        fun internalForrigeStegBekreftet(): Utfylt
        fun internalInstance(): Bekreftet
        companion object {
            fun createBekreftet(
                forrigeSteg: Utfylt,
                saksbehandler: NavIdentBruker.Saksbehandler,
                sakstype: Sakstype,
            ): Bekreftet {
                return when (forrigeSteg) {
                    is UtfyltOmgjør -> BekreftetOmgjøring.create(
                        forrigeSteg = forrigeSteg,
                        saksbehandler = saksbehandler,
                        sakstype = sakstype,
                    )
                    is UtfyltOppretthold -> BekreftetOpprettholdt.create(
                        forrigeSteg = forrigeSteg,
                        saksbehandler = saksbehandler,
                        sakstype = sakstype,
                    )

                    is UtfyltDelvisOmgjøringKA -> BekreftetDelvisOmgjøringKA.create(
                        forrigeSteg = forrigeSteg,
                        saksbehandler = saksbehandler,
                        sakstype = sakstype,
                    )
                }
            }
        }

        override fun erÅpen() = true

        override fun vurder(
            saksbehandler: NavIdentBruker.Saksbehandler,
            vurderinger: VurderingerTilKlage,
        ): VurdertKlage {
            return internalForrigeStegBekreftet().vurder(
                saksbehandler = saksbehandler,
                vurderinger = vurderinger,
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
                    saksbehandler = saksbehandler,
                    begrunnelse = begrunnelse,
                    avsluttetTidspunkt = tidspunktAvsluttet,
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    fnr = fnr,
                    journalpostId = journalpostId,
                    oppgaveId = oppgaveId,
                    datoKlageMottatt = datoKlageMottatt,
                    sakstype = sakstype,
                ).right()
            } else {
                KunneIkkeAvslutteKlage.UgyldigTilstand(this::class).left()
            }
        }
    }

    sealed interface BekreftetOversendtTilKA :
        BekreftetOversendtTilKAFelter,
        Bekreftet,
        KanGenerereBrevutkast {
        override val fritekstTilBrev: String
            get() = vurderinger.fritekstTilOversendelsesbrev

        override val fritekstTilVedtaksbrev: String
            get() = super.fritekstTilVedtaksbrev
    }

    sealed interface BekreftetOversendtTilKAFelter : VurdertKlageFelter {

        override val vurderinger: VurderingerTilKlage.OversendtKA

        val fritekstTilVedtaksbrev: String
            get() = vurderinger.fritekstTilOversendelsesbrev
    }

    data class BekreftetDelvisOmgjøringKA internal constructor(
        private val forrigeSteg: UtfyltDelvisOmgjøringKA,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val oppgaveId: OppgaveId = forrigeSteg.oppgaveId,
        override val attesteringer: Attesteringshistorikk = forrigeSteg.attesteringer,
        override val klageinstanshendelser: Klageinstanshendelser = forrigeSteg.klageinstanshendelser,
        override val sakstype: Sakstype,
    ) : BekreftetOversendtTilKA,
        BekreftetOversendtTilKAFelter,
        VurdertKlageFelter by forrigeSteg {
        override val vurderinger: VurderingerTilKlage.UtfyltDelvisOmgjøringKA = forrigeSteg.vurderinger

        override fun sendTilAttestering(
            saksbehandler: NavIdentBruker.Saksbehandler,
        ): Either<KunneIkkeSendeKlageTilAttestering, KlageTilAttestering> {
            return KlageTilAttestering.Vurdert(
                forrigeSteg = this,
                saksbehandler = saksbehandler,
                sakstype = sakstype,
            ).right()
        }

        override fun bekreftVurderinger(
            saksbehandler: NavIdentBruker.Saksbehandler,
        ): Bekreftet {
            return this.copy(saksbehandler = saksbehandler)
        }
        override fun internalInstance(): Bekreftet {
            return this
        }
        override fun internalForrigeStegBekreftet() = forrigeSteg

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

        companion object {
            fun create(
                forrigeSteg: UtfyltDelvisOmgjøringKA,
                saksbehandler: NavIdentBruker.Saksbehandler,
                sakstype: Sakstype,
            ): BekreftetDelvisOmgjøringKA {
                return BekreftetDelvisOmgjøringKA(
                    forrigeSteg = forrigeSteg,
                    saksbehandler = saksbehandler,
                    sakstype = sakstype,
                )
            }
        }
    }

    data class BekreftetOpprettholdt internal constructor(
        private val forrigeSteg: UtfyltOppretthold,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val oppgaveId: OppgaveId = forrigeSteg.oppgaveId,
        override val attesteringer: Attesteringshistorikk = forrigeSteg.attesteringer,
        override val klageinstanshendelser: Klageinstanshendelser = forrigeSteg.klageinstanshendelser,
        override val sakstype: Sakstype,
    ) : BekreftetOversendtTilKA,
        BekreftetOversendtTilKAFelter,
        VurdertKlageFelter by forrigeSteg {
        override val vurderinger: VurderingerTilKlage.UtfyltOppretthold = forrigeSteg.vurderinger

        override fun sendTilAttestering(
            saksbehandler: NavIdentBruker.Saksbehandler,
        ): Either<KunneIkkeSendeKlageTilAttestering, KlageTilAttestering> {
            return KlageTilAttestering.Vurdert(
                forrigeSteg = this,
                saksbehandler = saksbehandler,
                sakstype = sakstype,
            ).right()
        }

        override fun bekreftVurderinger(
            saksbehandler: NavIdentBruker.Saksbehandler,
        ): Bekreftet {
            return this.copy(saksbehandler = saksbehandler)
        }
        override fun internalInstance(): Bekreftet {
            return this
        }
        override fun internalForrigeStegBekreftet() = forrigeSteg

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

        companion object {
            fun create(
                forrigeSteg: UtfyltOppretthold,
                saksbehandler: NavIdentBruker.Saksbehandler,
                sakstype: Sakstype,
            ): BekreftetOpprettholdt {
                return BekreftetOpprettholdt(
                    forrigeSteg = forrigeSteg,
                    saksbehandler = saksbehandler,
                    sakstype = sakstype,
                )
            }
        }
    }

    interface OmgjøringKlageFelter : VurdertKlageFelter {
        override val vurderinger: VurderingerTilKlage.UtfyltOmgjøring
    }

    data class BekreftetOmgjøring internal constructor(
        private val forrigeSteg: UtfyltOmgjør,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val oppgaveId: OppgaveId = forrigeSteg.oppgaveId,
        override val attesteringer: Attesteringshistorikk = forrigeSteg.attesteringer,
        override val klageinstanshendelser: Klageinstanshendelser = forrigeSteg.klageinstanshendelser,
        override val sakstype: Sakstype,
    ) : Bekreftet,
        OmgjøringKlageFelter,
        VurdertKlageFelter by forrigeSteg {
        override val vurderinger: VurderingerTilKlage.UtfyltOmgjøring = forrigeSteg.vurderinger

        companion object {
            fun create(
                forrigeSteg: UtfyltOmgjør,
                saksbehandler: NavIdentBruker.Saksbehandler,
                sakstype: Sakstype,
            ): BekreftetOmgjøring {
                return BekreftetOmgjøring(
                    forrigeSteg = forrigeSteg,
                    saksbehandler = saksbehandler,
                    sakstype = sakstype,
                )
            }
        }

        override fun bekreftVurderinger(
            saksbehandler: NavIdentBruker.Saksbehandler,
        ): Bekreftet {
            return this.copy(saksbehandler = saksbehandler)
        }

        override fun ferdigstillOmgjøring(
            saksbehandler: NavIdentBruker.Saksbehandler,
            ferdigstiltTidspunkt: Tidspunkt,
        ): Either<KunneIkkeFerdigstilleOmgjøringsKlage, FerdigstiltOmgjortKlage> {
            return FerdigstiltOmgjortKlage(
                forrigeSteg = this,
                saksbehandler = saksbehandler,
                sakstype = sakstype,
                klageinstanshendelser = this.klageinstanshendelser,
                datoklageferdigstilt = ferdigstiltTidspunkt,
            ).right()
        }

        override fun internalInstance(): Bekreftet {
            return this
        }
        override fun internalForrigeStegBekreftet() = forrigeSteg
        override val fritekstTilBrev: String?
            get() = null
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
