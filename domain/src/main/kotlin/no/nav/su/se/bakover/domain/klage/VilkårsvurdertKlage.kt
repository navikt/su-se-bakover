package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import behandling.klage.domain.FormkravTilKlage
import behandling.klage.domain.KlageId
import behandling.klage.domain.VilkårsvurdertKlageFelter
import behandling.klage.domain.VurderingerTilKlage
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.attestering.Attesteringshistorikk
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.LocalDate
import java.util.UUID

/*
    Tilstanden representerer klagen der formkravene blir vurdert.
 */
sealed interface VilkårsvurdertKlage :
    Klage,
    VilkårsvurdertKlageFelter {

    data class Påbegynt(
        override val id: KlageId,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val journalpostId: JournalpostId,
        override val oppgaveId: OppgaveId,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val vilkårsvurderinger: FormkravTilKlage.Påbegynt,
        override val attesteringer: Attesteringshistorikk,
        override val datoKlageMottatt: LocalDate,
        override val sakstype: Sakstype,
    ) : VilkårsvurdertKlage {

        override fun erÅpen() = true
        override fun erAvsluttet() = false
        override fun erAvbrutt() = false

        /**
         * Denne er avhengig av sjekken på formkravene i [FormkravTilKlage].create()
         */
        override fun vilkårsvurder(
            saksbehandler: NavIdentBruker.Saksbehandler,
            vilkårsvurderinger: FormkravTilKlage,
        ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
            return when (vilkårsvurderinger) {
                is FormkravTilKlage.Utfylt -> Utfylt.create(
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
                    sakstype = sakstype,
                )

                is FormkravTilKlage.Påbegynt -> Påbegynt(
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

        override val vilkårsvurderinger: FormkravTilKlage.Utfylt

        /**
         * En vilkårsvurdert avvist representerer en klage der minst et av vilkårene er blitt besvart 'nei/false'
         */
        data class Avvist(
            override val id: KlageId,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val fnr: Fnr,
            override val journalpostId: JournalpostId,
            override val oppgaveId: OppgaveId,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val vilkårsvurderinger: FormkravTilKlage.Utfylt,
            override val attesteringer: Attesteringshistorikk,
            override val datoKlageMottatt: LocalDate,
            // Ønsker å ta vare på dette feltet dersom vi går tilbake til vilkårsvurderingen igjen.
            val fritekstTilVedtaksbrev: String?,
            override val sakstype: Sakstype,
        ) : Utfylt {

            override fun erÅpen() = true
            override fun erAvsluttet() = false
            override fun erAvbrutt() = false

            override fun vilkårsvurder(
                saksbehandler: NavIdentBruker.Saksbehandler,
                vilkårsvurderinger: FormkravTilKlage,
            ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
                return when (vilkårsvurderinger) {
                    is FormkravTilKlage.Utfylt -> create(
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
                        sakstype = sakstype,
                    )

                    is FormkravTilKlage.Påbegynt -> Påbegynt(
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
                    sakstype = sakstype,
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
            override val id: KlageId,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val fnr: Fnr,
            override val journalpostId: JournalpostId,
            override val oppgaveId: OppgaveId,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val vilkårsvurderinger: FormkravTilKlage.Utfylt,
            override val attesteringer: Attesteringshistorikk,
            override val datoKlageMottatt: LocalDate,
            override val vurderinger: VurderingerTilKlage?,
            override val klageinstanshendelser: Klageinstanshendelser,
            override val sakstype: Sakstype,
        ) : Utfylt,
            TilVurderingFelter {

            val fritekstTilBrev: String?
                get() =
                    when (val vurderinger = vurderinger) {
                        is VurderingerTilKlage.Påbegynt -> vurderinger.fritekstTilOversendelsesbrev
                        is VurderingerTilKlage.UtfyltOmgjøring -> null
                        is VurderingerTilKlage.UtfyltOppretthold -> vurderinger.fritekstTilOversendelsesbrev
                        null -> null
                    }

            override fun erÅpen() = true
            override fun erAvsluttet() = false
            override fun erAvbrutt() = false

            override fun vilkårsvurder(
                saksbehandler: NavIdentBruker.Saksbehandler,
                vilkårsvurderinger: FormkravTilKlage,
            ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
                if (klageinstanshendelser.isNotEmpty() && vilkårsvurderinger.erAvvist()) {
                    return KunneIkkeVilkårsvurdereKlage.KanIkkeAvviseEnKlageSomHarVærtOversendt.left()
                }

                return when (vilkårsvurderinger) {
                    is FormkravTilKlage.Utfylt -> create(
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
                        sakstype = sakstype,
                    )

                    is FormkravTilKlage.Påbegynt -> Påbegynt(
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

        companion object {
            fun create(
                id: KlageId,
                opprettet: Tidspunkt,
                sakId: UUID,
                saksnummer: Saksnummer,
                sakstype: Sakstype,
                fnr: Fnr,
                journalpostId: JournalpostId,
                oppgaveId: OppgaveId,
                saksbehandler: NavIdentBruker.Saksbehandler,
                vilkårsvurderinger: FormkravTilKlage.Utfylt,
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
                        sakstype = sakstype,
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
                    sakstype = sakstype,
                )
            }
        }
    }

    interface BekreftetFelter : VilkårsvurdertKlageFelter {
        override val vilkårsvurderinger: FormkravTilKlage.Utfylt
    }

    /**
     * Denne bekreftet representer en klage som er blitt utfylt, og saksbehandler har gått et steg videre i prosessen
     * Her vil dem starte vurderingen, eller avvisningen.
     */
    sealed interface Bekreftet :
        VilkårsvurdertKlage,
        BekreftetFelter {

        data class Avvist(
            override val id: KlageId,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val fnr: Fnr,
            override val journalpostId: JournalpostId,
            override val oppgaveId: OppgaveId,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val vilkårsvurderinger: FormkravTilKlage.Utfylt,
            override val attesteringer: Attesteringshistorikk,
            override val datoKlageMottatt: LocalDate,
            // Så vi kan ta vare på fritekst hvis vi går tilbake til vilkårsvurderingen igjen.
            val fritekstTilAvvistVedtaksbrev: String?,
            override val sakstype: Sakstype,
        ) : Bekreftet,
            BekreftetFelter,
            KanLeggeTilFritekstTilAvvistBrev {

            override fun erÅpen() = true
            override fun erAvsluttet() = false
            override fun erAvbrutt() = false

            override fun vilkårsvurder(
                saksbehandler: NavIdentBruker.Saksbehandler,
                vilkårsvurderinger: FormkravTilKlage,
            ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
                return when (vilkårsvurderinger) {
                    is FormkravTilKlage.Utfylt -> Utfylt.create(
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
                        sakstype = sakstype,
                    )

                    is FormkravTilKlage.Påbegynt -> Påbegynt(
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
                    sakstype = sakstype,
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
                    sakstype = sakstype,
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
            override val id: KlageId,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val saksnummer: Saksnummer,
            override val fnr: Fnr,
            override val journalpostId: JournalpostId,
            override val oppgaveId: OppgaveId,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val vilkårsvurderinger: FormkravTilKlage.Utfylt,
            override val attesteringer: Attesteringshistorikk,
            override val datoKlageMottatt: LocalDate,
            override val vurderinger: VurderingerTilKlage?,
            override val klageinstanshendelser: Klageinstanshendelser,
            override val sakstype: Sakstype,
        ) : Bekreftet,
            TilVurderingFelter,
            KlageSomKanVurderes {

            val fritekstTilBrev: String?
                get() =
                    when (val vurderinger = vurderinger) {
                        is VurderingerTilKlage.Påbegynt -> vurderinger.fritekstTilOversendelsesbrev
                        is VurderingerTilKlage.UtfyltOmgjøring -> null
                        is VurderingerTilKlage.UtfyltOppretthold -> vurderinger.fritekstTilOversendelsesbrev
                        null -> null
                    }
            override fun erÅpen() = true
            override fun erAvsluttet() = false
            override fun erAvbrutt() = false

            override fun vilkårsvurder(
                saksbehandler: NavIdentBruker.Saksbehandler,
                vilkårsvurderinger: FormkravTilKlage,
            ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
                if (klageinstanshendelser.isNotEmpty() && vilkårsvurderinger.erAvvist()) {
                    return KunneIkkeVilkårsvurdereKlage.KanIkkeAvviseEnKlageSomHarVærtOversendt.left()
                }

                return when (vilkårsvurderinger) {
                    is FormkravTilKlage.Utfylt -> Utfylt.create(
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
                        sakstype = sakstype,
                    )

                    is FormkravTilKlage.Påbegynt -> Påbegynt(
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
                    sakstype = sakstype,
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
                    sakstype = sakstype,
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
}
