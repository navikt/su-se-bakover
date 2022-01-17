package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
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

    override fun vilkårsvurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkårsvurderinger: VilkårsvurderingerTilKlage,
    ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
        val vurderinger = when (this) {
            is Påbegynt -> null
            is Bekreftet.Avvist -> null
            is Utfylt.Avvist -> null
            is AvvistKlage -> null

            is Bekreftet.TilVurdering -> this.vurderinger
            is Utfylt.TilVurdering -> this.vurderinger
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
                vurderinger = vurderinger,
                attesteringer = attesteringer,
                datoKlageMottatt = datoKlageMottatt,
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
                klagevedtakshistorikk = klagevedtakshistorikk,
            )
        }.right()
    }

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
        override val klagevedtakshistorikk: Klagevedtakshistorikk,
    ) : VilkårsvurdertKlage {

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
                klagevedtakshistorikk: Klagevedtakshistorikk,
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
                klagevedtakshistorikk = klagevedtakshistorikk
            )
        }
    }

    /**
     * Denne tilstanden representerer en klage når alle vilkårsvurderingene er blitt fylt ut, og ikke har blitt bekreftet
     */
    sealed class Utfylt : VilkårsvurdertKlage {
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
            override val klagevedtakshistorikk: Klagevedtakshistorikk,
        ) : Utfylt() {

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
                    klagevedtakshistorikk = klagevedtakshistorikk,
                ).right()
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
                    attesteringer: Attesteringshistorikk,
                    datoKlageMottatt: LocalDate,
                    klagevedtakshistorikk: Klagevedtakshistorikk,
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
                        klagevedtakshistorikk = klagevedtakshistorikk,
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
            override val klagevedtakshistorikk: Klagevedtakshistorikk,
            /**
             * Siden det er mulig å gå tilbake fra [VurdertKlage] til [VilkårsvurdertKlage] må vå holde på den informasjon.
             */
            val vurderinger: VurderingerTilKlage?,
        ) : Utfylt() {

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
                        klagevedtakshistorikk = klagevedtakshistorikk,
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
            override val klagevedtakshistorikk: Klagevedtakshistorikk,
        ) : Bekreftet() {
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
                    klagevedtakshistorikk = klagevedtakshistorikk,
                ).right()
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
                    attesteringer: Attesteringshistorikk,
                    datoKlageMottatt: LocalDate,
                    klagevedtakshistorikk: Klagevedtakshistorikk,
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
                        klagevedtakshistorikk = klagevedtakshistorikk,
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
            override val klagevedtakshistorikk: Klagevedtakshistorikk,
            val vurderinger: VurderingerTilKlage?,
        ) : Bekreftet() {

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
                    klagevedtakshistorikk = klagevedtakshistorikk
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
                klagevedtakshistorikk: Klagevedtakshistorikk
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
                        klagevedtakshistorikk = klagevedtakshistorikk,
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

sealed class KunneIkkeVilkårsvurdereKlage {
    object FantIkkeKlage : KunneIkkeVilkårsvurdereKlage()
    object FantIkkeVedtak : KunneIkkeVilkårsvurdereKlage()
    data class UgyldigTilstand(val fra: KClass<out Klage>, val til: KClass<out Klage>) : KunneIkkeVilkårsvurdereKlage()
}
