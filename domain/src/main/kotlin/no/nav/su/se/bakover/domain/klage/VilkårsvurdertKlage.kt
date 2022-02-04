package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

sealed interface VilkårsvurdertKlage : Klage {

    val vilkårsvurderinger: VilkårsvurderingerTilKlage
    val attesteringer: Attesteringshistorikk

    data class Påbegynt private constructor(
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
                    klagevedtakshistorikk = Klagevedtakshistorikk.empty(),
                )
                is VilkårsvurderingerTilKlage.Påbegynt -> create(
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
            forrigeSteg = this,
            saksbehandler = saksbehandler,
            begrunnelse = begrunnelse,
            tidspunktAvsluttet = tidspunktAvsluttet,
        ).right()

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
                vilkårsvurderinger: VilkårsvurderingerTilKlage.Påbegynt,
                attesteringer: Attesteringshistorikk,
                datoKlageMottatt: LocalDate,
            ) = Påbegynt(
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
        }
    }

    /**
     * Denne tilstanden representerer en klage når alle vilkårsvurderingene er blitt fylt ut, og ikke har blitt bekreftet
     */
    sealed interface Utfylt : VilkårsvurdertKlage {
        abstract override val id: UUID
        abstract override val opprettet: Tidspunkt
        abstract override val sakId: UUID
        abstract override val saksnummer: Saksnummer
        abstract override val fnr: Fnr
        abstract override val journalpostId: JournalpostId
        abstract override val oppgaveId: OppgaveId
        abstract override val saksbehandler: NavIdentBruker.Saksbehandler
        abstract override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt
        abstract override val attesteringer: Attesteringshistorikk
        abstract override val datoKlageMottatt: LocalDate

        /**
         * En vilkårsvurdert avvist representerer en klage der minst et av vilkårene er blitt besvart 'nei/false'
         */
        data class Avvist private constructor(
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
                        vurderinger = null,
                        klagevedtakshistorikk = Klagevedtakshistorikk.empty(),
                    )
                    is VilkårsvurderingerTilKlage.Påbegynt -> Påbegynt.create(
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
                return Bekreftet.Avvist.create(
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
                ).right()
            }

            override fun kanAvsluttes() = true

            override fun avslutt(
                saksbehandler: NavIdentBruker.Saksbehandler,
                begrunnelse: String,
                tidspunktAvsluttet: Tidspunkt,
            ) = AvsluttetKlage(
                forrigeSteg = this,
                saksbehandler = saksbehandler,
                begrunnelse = begrunnelse,
                tidspunktAvsluttet = tidspunktAvsluttet,
            ).right()

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
                    attesteringer: Attesteringshistorikk,
                    datoKlageMottatt: LocalDate,
                ): Avvist {
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
                    )
                }
            }
        }

        /**
         * En vilkårsvurdert avvist representerer en klage alle vilkårene oppfylt
         */
        data class TilVurdering private constructor(
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
            val vurderinger: VurderingerTilKlage?,
            val klagevedtakshistorikk: Klagevedtakshistorikk,
        ) : Utfylt {

            override fun erÅpen() = true

            override fun vilkårsvurder(
                saksbehandler: NavIdentBruker.Saksbehandler,
                vilkårsvurderinger: VilkårsvurderingerTilKlage,
            ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {

                if (klagevedtakshistorikk.isNotEmpty() && vilkårsvurderinger.erAvvist()) {
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
                        klagevedtakshistorikk = klagevedtakshistorikk,
                        vurderinger = vurderinger,
                    )
                    is VilkårsvurderingerTilKlage.Påbegynt -> Påbegynt.create(
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
                return Bekreftet.TilVurdering.create(
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
                    klagevedtakshistorikk = klagevedtakshistorikk,
                ).right()
            }

            override fun kanAvsluttes() = klagevedtakshistorikk.isEmpty()

            override fun avslutt(
                saksbehandler: NavIdentBruker.Saksbehandler,
                begrunnelse: String,
                tidspunktAvsluttet: Tidspunkt,
            ): Either<KunneIkkeAvslutteKlage.UgyldigTilstand, AvsluttetKlage> {
                return if (klagevedtakshistorikk.isEmpty()) {
                    AvsluttetKlage(
                        forrigeSteg = this,
                        saksbehandler = saksbehandler,
                        begrunnelse = begrunnelse,
                        tidspunktAvsluttet = tidspunktAvsluttet,
                    ).right()
                } else KunneIkkeAvslutteKlage.UgyldigTilstand(this::class).left()
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
                    klagevedtakshistorikk: Klagevedtakshistorikk,
                ): TilVurdering {
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
                        klagevedtakshistorikk = klagevedtakshistorikk,
                    )
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
                klagevedtakshistorikk: Klagevedtakshistorikk,
            ): Utfylt {
                if (vilkårsvurderinger.erAvvist()) {
                    return Avvist.create(
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
                }

                return TilVurdering.create(
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
                    klagevedtakshistorikk = klagevedtakshistorikk,
                )
            }
        }
    }

    /**
     * Denne bekreftet representer en klage som er blitt utfylt, og saksbehandler har gått et steg videre i prosessen
     * Her vil dem starte vurderingen, eller avvisningen.
     */
    sealed class Bekreftet : VilkårsvurdertKlage {
        abstract override val id: UUID
        abstract override val opprettet: Tidspunkt
        abstract override val sakId: UUID
        abstract override val saksnummer: Saksnummer
        abstract override val fnr: Fnr
        abstract override val journalpostId: JournalpostId
        abstract override val oppgaveId: OppgaveId
        abstract override val saksbehandler: NavIdentBruker.Saksbehandler
        abstract override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt
        abstract override val attesteringer: Attesteringshistorikk
        abstract override val datoKlageMottatt: LocalDate

        data class Avvist private constructor(
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
        ) : Bekreftet() {

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
                        klagevedtakshistorikk = Klagevedtakshistorikk.empty(),
                    )
                    is VilkårsvurderingerTilKlage.Påbegynt -> Påbegynt.create(
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

            override fun leggTilAvvistFritekstTilBrev(
                saksbehandler: NavIdentBruker.Saksbehandler,
                fritekst: String,
            ): Either<KunneIkkeLeggeTilFritekstForAvvist.UgyldigTilstand, AvvistKlage> {
                return AvvistKlage.create(
                    forrigeSteg = this,
                    fritekstTilBrev = fritekst,
                ).right()
            }

            override fun bekreftVilkårsvurderinger(
                saksbehandler: NavIdentBruker.Saksbehandler,
            ): Either<KunneIkkeBekrefteKlagesteg.UgyldigTilstand, Bekreftet> {
                return create(
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
                ).right()
            }

            override fun kanAvsluttes() = true

            override fun avslutt(
                saksbehandler: NavIdentBruker.Saksbehandler,
                begrunnelse: String,
                tidspunktAvsluttet: Tidspunkt,
            ) = AvsluttetKlage(
                forrigeSteg = this,
                saksbehandler = saksbehandler,
                begrunnelse = begrunnelse,
                tidspunktAvsluttet = tidspunktAvsluttet,
            ).right()

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
                    attesteringer: Attesteringshistorikk,
                    datoKlageMottatt: LocalDate,
                ): Avvist {
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
                    )
                }
            }
        }

        data class TilVurdering private constructor(
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
            val vurderinger: VurderingerTilKlage?,
            val klagevedtakshistorikk: Klagevedtakshistorikk,
        ) : Bekreftet() {

            override fun erÅpen() = true

            override fun vilkårsvurder(
                saksbehandler: NavIdentBruker.Saksbehandler,
                vilkårsvurderinger: VilkårsvurderingerTilKlage,
            ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
                if (klagevedtakshistorikk.isNotEmpty() && vilkårsvurderinger.erAvvist()) {
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
                        klagevedtakshistorikk = klagevedtakshistorikk,
                    )
                    is VilkårsvurderingerTilKlage.Påbegynt -> Påbegynt.create(
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
            ): Either<KunneIkkeVurdereKlage.UgyldigTilstand, VurdertKlage> {
                return when (vurderinger) {
                    is VurderingerTilKlage.Påbegynt -> vurder(saksbehandler, vurderinger)
                    is VurderingerTilKlage.Utfylt -> vurder(saksbehandler, vurderinger)
                }.right()
            }

            fun vurder(
                saksbehandler: NavIdentBruker.Saksbehandler,
                vurderinger: VurderingerTilKlage.Påbegynt,
            ): VurdertKlage.Påbegynt {
                return VurdertKlage.Påbegynt.create(
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
                    klagevedtakshistorikk = klagevedtakshistorikk,
                )
            }

            fun vurder(
                saksbehandler: NavIdentBruker.Saksbehandler,
                vurderinger: VurderingerTilKlage.Utfylt,
            ): VurdertKlage.Utfylt {
                return VurdertKlage.Utfylt.create(
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
                    klagevedtakshistorikk = klagevedtakshistorikk,
                )
            }

            override fun bekreftVilkårsvurderinger(
                saksbehandler: NavIdentBruker.Saksbehandler,
            ): Either<KunneIkkeBekrefteKlagesteg.UgyldigTilstand, Bekreftet> {
                return Bekreftet.create(
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
                    klagevedtakshistorikk = klagevedtakshistorikk,
                ).right()
            }

            override fun kanAvsluttes() = klagevedtakshistorikk.isEmpty()

            override fun avslutt(
                saksbehandler: NavIdentBruker.Saksbehandler,
                begrunnelse: String,
                tidspunktAvsluttet: Tidspunkt,
            ): Either<KunneIkkeAvslutteKlage.UgyldigTilstand, AvsluttetKlage> {
                return if (klagevedtakshistorikk.isEmpty()) {
                    AvsluttetKlage(
                        forrigeSteg = this,
                        saksbehandler = saksbehandler,
                        begrunnelse = begrunnelse,
                        tidspunktAvsluttet = tidspunktAvsluttet,
                    ).right()
                } else KunneIkkeAvslutteKlage.UgyldigTilstand(this::class).left()
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
                    klagevedtakshistorikk: Klagevedtakshistorikk,
                ): TilVurdering {
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
                        klagevedtakshistorikk = klagevedtakshistorikk,
                    )
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
                klagevedtakshistorikk: Klagevedtakshistorikk,
            ): Bekreftet {
                if (vilkårsvurderinger.erAvvist()) {
                    return Avvist.create(
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
                }

                return TilVurdering.create(
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
                    klagevedtakshistorikk = klagevedtakshistorikk,
                )
            }
        }
    }

    companion object {
        internal fun VilkårsvurderingerTilKlage.erAvvist(): Boolean {
            return this.klagesDetPåKonkreteElementerIVedtaket == false ||
                this.innenforFristen == VilkårsvurderingerTilKlage.Svarord.NEI ||
                this.erUnderskrevet == VilkårsvurderingerTilKlage.Svarord.NEI
        }
    }
}

sealed interface KunneIkkeVilkårsvurdereKlage {
    object FantIkkeKlage : KunneIkkeVilkårsvurdereKlage
    object FantIkkeVedtak : KunneIkkeVilkårsvurdereKlage
    object KanIkkeAvviseEnKlageSomHarVærtOversendt : KunneIkkeVilkårsvurdereKlage
    data class UgyldigTilstand(val fra: KClass<out Klage>, val til: KClass<out Klage>) : KunneIkkeVilkårsvurdereKlage
}
