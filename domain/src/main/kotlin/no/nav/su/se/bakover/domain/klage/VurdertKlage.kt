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

sealed class VurdertKlage : Klage() {

    abstract val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt
    abstract val vurderinger: VurderingerTilKlage
    abstract val attesteringer: Attesteringshistorikk

    override fun vilkårsvurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkårsvurderinger: VilkårsvurderingerTilKlage,
    ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
        return when (vilkårsvurderinger) {
            is VilkårsvurderingerTilKlage.Påbegynt -> VilkårsvurdertKlage.Påbegynt.create(
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
                klagevedtakshistorikk = klagevedtakshistorikk
            )
        }.right()
    }

    override fun vurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vurderinger: VurderingerTilKlage,
    ): Either<KunneIkkeVurdereKlage.UgyldigTilstand, VurdertKlage> {
        return when (vurderinger) {
            is VurderingerTilKlage.Påbegynt -> Påbegynt.create(
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
            is VurderingerTilKlage.Utfylt -> Utfylt.create(
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
        }.right()
    }

    override fun bekreftVilkårsvurderinger(saksbehandler: NavIdentBruker.Saksbehandler): Either<KunneIkkeBekrefteKlagesteg.UgyldigTilstand, VilkårsvurdertKlage.Bekreftet> {
        return VilkårsvurdertKlage.Bekreftet.create(
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
        ).right()
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
        override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
        override val vurderinger: VurderingerTilKlage.Påbegynt,
        override val attesteringer: Attesteringshistorikk,
        override val datoKlageMottatt: LocalDate,
        override val klagevedtakshistorikk: Klagevedtakshistorikk,
    ) : VurdertKlage() {
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
                vurderinger: VurderingerTilKlage.Påbegynt,
                attesteringer: Attesteringshistorikk,
                datoKlageMottatt: LocalDate,
                klagevedtakshistorikk: Klagevedtakshistorikk
            ): Påbegynt {
                return Påbegynt(
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
        }
    }

    data class Utfylt private constructor(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val journalpostId: JournalpostId,
        override val oppgaveId: OppgaveId,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
        override val vurderinger: VurderingerTilKlage.Utfylt,
        override val attesteringer: Attesteringshistorikk,
        override val datoKlageMottatt: LocalDate,
        override val klagevedtakshistorikk: Klagevedtakshistorikk,
    ) : VurdertKlage() {

        override fun bekreftVurderinger(
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
                klagevedtakshistorikk = klagevedtakshistorikk
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
                vurderinger: VurderingerTilKlage.Utfylt,
                attesteringer: Attesteringshistorikk,
                datoKlageMottatt: LocalDate,
                klagevedtakshistorikk: Klagevedtakshistorikk
            ): Utfylt {
                return Utfylt(
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
        }
    }

    /**
     * Denne tilstanden representerer klagen når den er i oppsummerings-steget i behandlingen,
     * men også når den har blitt underkjent
     */
    data class Bekreftet private constructor(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val journalpostId: JournalpostId,
        override val oppgaveId: OppgaveId,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
        override val vurderinger: VurderingerTilKlage.Utfylt,
        override val attesteringer: Attesteringshistorikk,
        override val datoKlageMottatt: LocalDate,
        override val klagevedtakshistorikk: Klagevedtakshistorikk,
    ) : VurdertKlage() {

        override fun bekreftVurderinger(
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
                vurderinger = vurderinger,
                attesteringer = attesteringer,
                datoKlageMottatt = datoKlageMottatt,
                klagevedtakshistorikk = klagevedtakshistorikk
            ).right()
        }

        override fun sendTilAttestering(
            saksbehandler: NavIdentBruker.Saksbehandler,
            opprettOppgave: () -> Either<KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave, OppgaveId>,
        ): Either<KunneIkkeSendeTilAttestering, KlageTilAttestering> {
            return opprettOppgave().map { oppgaveId ->
                KlageTilAttestering.create(
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
                vurderinger: VurderingerTilKlage.Utfylt,
                attesteringer: Attesteringshistorikk,
                datoKlageMottatt: LocalDate,
                klagevedtakshistorikk: Klagevedtakshistorikk
            ): Bekreftet {
                return Bekreftet(
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
        }
    }
}

sealed class KunneIkkeVurdereKlage {
    object FantIkkeKlage : KunneIkkeVurdereKlage()
    object UgyldigOmgjøringsårsak : KunneIkkeVurdereKlage()
    object UgyldigOmgjøringsutfall : KunneIkkeVurdereKlage()
    object UgyldigOpprettholdelseshjemler : KunneIkkeVurdereKlage()
    object KanIkkeVelgeBådeOmgjørOgOppretthold : KunneIkkeVurdereKlage()
    data class UgyldigTilstand(val fra: KClass<out Klage>, val til: KClass<out Klage>) : KunneIkkeVurdereKlage()
}
