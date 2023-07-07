package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.sak.Saksnummer
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

interface VilkårsvurdertKlageFelter : Klagefelter {
    val vilkårsvurderinger: VilkårsvurderingerTilKlage
    val attesteringer: Attesteringshistorikk
}

sealed interface VilkårsvurdertKlage : Klage, VilkårsvurdertKlageFelter {

    data class Påbegynt(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val journalpostId: JournalpostId,
        override val oppgaveId: OppgaveId,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Påbegynt,
        override val attesteringer: Attesteringshistorikk,
        override val datoKlageMottatt: LocalDate,
    ) : VilkårsvurdertKlage {

        override fun erÅpen() = true

        override fun vilkårsvurder(
            saksbehandler: NavIdentBruker.Saksbehandler,
            vilkårsvurderinger: VilkårsvurderingerTilKlage,
        ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
            return when (vilkårsvurderinger) {
                is VilkårsvurderingerTilKlage.Utfylt -> Utfylt.create(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    fnr = fnr,
                    journalpostId = journalpostId,
                    oppgaveId = oppgaveId,
                    saksbehandler = saksbehandler,
                    vilkårsvurderinger = vilkårsvurderinger,
                    vurderinger = null,
                    attesteringer = attesteringer,
                    datoKlageMottatt = datoKlageMottatt,
                    klageinstanshendelser = Klageinstanshendelser.empty(),
                    fritekstTilAvvistVedtaksbrev = null,
                )
                is VilkårsvurderingerTilKlage.Påbegynt -> Påbegynt(
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

    /**
     * Denne tilstanden representerer en klage når alle vilkårsvurderingene er blitt fylt ut, og ikke har blitt bekreftet
     */
    sealed interface Utfylt : VilkårsvurdertKlage {

        override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt

        /**
         * En vilkårsvurdert avvist representerer en klage der minst et av vilkårene er blitt besvart 'nei/false'
         */
        data class Avvist(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val fnr: Fnr,
            override val journalpostId: JournalpostId,
            override val oppgaveId: OppgaveId,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
            override val attesteringer: Attesteringshistorikk,
            override val datoKlageMottatt: LocalDate,
            // Ønsker å ta vare på dette feltet dersom vi går tilbake til vilkårsvurderingen igjen.
            val fritekstTilVedtaksbrev: String?,
        ) : Utfylt {

            override fun erÅpen() = true

            override fun vilkårsvurder(
                saksbehandler: NavIdentBruker.Saksbehandler,
                vilkårsvurderinger: VilkårsvurderingerTilKlage,
            ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
                return when (vilkårsvurderinger) {
                    is VilkårsvurderingerTilKlage.Utfylt -> create(
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
                        vurderinger = null,
                        klageinstanshendelser = Klageinstanshendelser.empty(),
                    )
                    is VilkårsvurderingerTilKlage.Påbegynt -> Påbegynt(
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

            override fun bekreftVilkårsvurderinger(
                saksbehandler: NavIdentBruker.Saksbehandler,
            ): Either<KunneIkkeBekrefteKlagesteg.UgyldigTilstand, Bekreftet> {
                return Bekreftet.Avvist(
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

        interface TilVurderingFelter : VilkårsvurdertKlageFelter {
            val vurderinger: VurderingerTilKlage?
            val klageinstanshendelser: Klageinstanshendelser
        }

        /**
         * En vilkårsvurdert avvist representerer en klage alle vilkårene oppfylt
         */
        data class TilVurdering(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val fnr: Fnr,
            override val journalpostId: JournalpostId,
            override val oppgaveId: OppgaveId,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
            override val attesteringer: Attesteringshistorikk,
            override val datoKlageMottatt: LocalDate,
            override val vurderinger: VurderingerTilKlage?,
            override val klageinstanshendelser: Klageinstanshendelser,
        ) : Utfylt, TilVurderingFelter {

            override fun erÅpen() = true

            override fun vilkårsvurder(
                saksbehandler: NavIdentBruker.Saksbehandler,
                vilkårsvurderinger: VilkårsvurderingerTilKlage,
            ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
                if (klageinstanshendelser.isNotEmpty() && vilkårsvurderinger.erAvvist()) {
                    return KunneIkkeVilkårsvurdereKlage.KanIkkeAvviseEnKlageSomHarVærtOversendt.left()
                }

                return when (vilkårsvurderinger) {
                    is VilkårsvurderingerTilKlage.Utfylt -> create(
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
                        klageinstanshendelser = klageinstanshendelser,
                        vurderinger = vurderinger,
                        fritekstTilAvvistVedtaksbrev = null,
                    )
                    is VilkårsvurderingerTilKlage.Påbegynt -> Påbegynt(
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

            override fun bekreftVilkårsvurderinger(
                saksbehandler: NavIdentBruker.Saksbehandler,
            ): Either<KunneIkkeBekrefteKlagesteg.UgyldigTilstand, Bekreftet> {
                return Bekreftet.TilVurdering(
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
                vurderinger: VurderingerTilKlage?,
                attesteringer: Attesteringshistorikk,
                datoKlageMottatt: LocalDate,
                klageinstanshendelser: Klageinstanshendelser,
                fritekstTilAvvistVedtaksbrev: String?,
            ): Utfylt {
                if (vilkårsvurderinger.erAvvist()) {
                    return Avvist(
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
                        fritekstTilVedtaksbrev = fritekstTilAvvistVedtaksbrev,
                    )
                }

                return TilVurdering(
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
    }

    interface BekreftetFelter : VilkårsvurdertKlageFelter {
        override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt
    }

    /**
     * Denne bekreftet representer en klage som er blitt utfylt, og saksbehandler har gått et steg videre i prosessen
     * Her vil dem starte vurderingen, eller avvisningen.
     */
    sealed interface Bekreftet : VilkårsvurdertKlage, BekreftetFelter {

        data class Avvist(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val fnr: Fnr,
            override val journalpostId: JournalpostId,
            override val oppgaveId: OppgaveId,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
            override val attesteringer: Attesteringshistorikk,
            override val datoKlageMottatt: LocalDate,
            // Så vi kan ta vare på fritekst hvis vi går tilbake til vilkårsvurderingen igjen.
            val fritekstTilAvvistVedtaksbrev: String?,
        ) : Bekreftet, BekreftetFelter, KanLeggeTilFritekstTilAvvistBrev {

            override fun erÅpen() = true

            override fun vilkårsvurder(
                saksbehandler: NavIdentBruker.Saksbehandler,
                vilkårsvurderinger: VilkårsvurderingerTilKlage,
            ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
                return when (vilkårsvurderinger) {
                    is VilkårsvurderingerTilKlage.Utfylt -> Utfylt.create(
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
                        fritekstTilAvvistVedtaksbrev = fritekstTilAvvistVedtaksbrev,
                    )
                    is VilkårsvurderingerTilKlage.Påbegynt -> Påbegynt(
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

            override fun leggTilFritekstTilAvvistVedtaksbrev(
                saksbehandler: NavIdentBruker.Saksbehandler,
                fritekstTilAvvistVedtaksbrev: String,
            ): AvvistKlage {
                return AvvistKlage(
                    forrigeSteg = this,
                    saksbehandler = saksbehandler,
                    fritekstTilVedtaksbrev = fritekstTilAvvistVedtaksbrev,
                )
            }

            override fun bekreftVilkårsvurderinger(
                saksbehandler: NavIdentBruker.Saksbehandler,
            ): Either<KunneIkkeBekrefteKlagesteg.UgyldigTilstand, Avvist> {
                return Avvist(
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
                    fritekstTilAvvistVedtaksbrev = fritekstTilAvvistVedtaksbrev,
                ).right()
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

        interface TilVurderingFelter : BekreftetFelter {
            val vurderinger: VurderingerTilKlage?
            val klageinstanshendelser: Klageinstanshendelser
        }

        data class TilVurdering(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val fnr: Fnr,
            override val journalpostId: JournalpostId,
            override val oppgaveId: OppgaveId,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
            override val attesteringer: Attesteringshistorikk,
            override val datoKlageMottatt: LocalDate,
            override val vurderinger: VurderingerTilKlage?,
            override val klageinstanshendelser: Klageinstanshendelser,
        ) : Bekreftet, TilVurderingFelter, KlageSomKanVurderes {

            override fun erÅpen() = true

            override fun vilkårsvurder(
                saksbehandler: NavIdentBruker.Saksbehandler,
                vilkårsvurderinger: VilkårsvurderingerTilKlage,
            ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
                if (klageinstanshendelser.isNotEmpty() && vilkårsvurderinger.erAvvist()) {
                    return KunneIkkeVilkårsvurdereKlage.KanIkkeAvviseEnKlageSomHarVærtOversendt.left()
                }

                return when (vilkårsvurderinger) {
                    is VilkårsvurderingerTilKlage.Utfylt -> Utfylt.create(
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
                        vurderinger = vurderinger,
                        klageinstanshendelser = klageinstanshendelser,
                        fritekstTilAvvistVedtaksbrev = null,
                    )
                    is VilkårsvurderingerTilKlage.Påbegynt -> Påbegynt(
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

            override fun vurder(
                saksbehandler: NavIdentBruker.Saksbehandler,
                vurderinger: VurderingerTilKlage,
            ): VurdertKlage {
                return when (vurderinger) {
                    is VurderingerTilKlage.Påbegynt -> vurder(saksbehandler, vurderinger)
                    is VurderingerTilKlage.Utfylt -> vurder(saksbehandler, vurderinger)
                }
            }

            fun vurder(
                saksbehandler: NavIdentBruker.Saksbehandler,
                vurderinger: VurderingerTilKlage.Påbegynt,
            ): VurdertKlage.Påbegynt {
                return VurdertKlage.Påbegynt(
                    forrigeSteg = this,
                    saksbehandler = saksbehandler,
                    vurderinger = vurderinger,
                )
            }

            fun vurder(
                saksbehandler: NavIdentBruker.Saksbehandler,
                vurderinger: VurderingerTilKlage.Utfylt,
            ): VurdertKlage.Utfylt {
                return VurdertKlage.Utfylt(
                    forrigeSteg = this.vurder(
                        saksbehandler = saksbehandler,
                        // Her hopper vi over [VurdertKlage.Påbegynt] steget
                        vurderinger = VurderingerTilKlage.empty(),
                    ),
                    saksbehandler = saksbehandler,
                    vurderinger = vurderinger,
                )
            }

            override fun bekreftVilkårsvurderinger(
                saksbehandler: NavIdentBruker.Saksbehandler,
            ): Either<KunneIkkeBekrefteKlagesteg.UgyldigTilstand, TilVurdering> {
                return TilVurdering(
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
}

sealed interface KunneIkkeVilkårsvurdereKlage {
    object FantIkkeKlage : KunneIkkeVilkårsvurdereKlage
    object FantIkkeVedtak : KunneIkkeVilkårsvurdereKlage
    object KanIkkeAvviseEnKlageSomHarVærtOversendt : KunneIkkeVilkårsvurdereKlage
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeVilkårsvurdereKlage {
        val til = VilkårsvurdertKlage::class
    }
}
