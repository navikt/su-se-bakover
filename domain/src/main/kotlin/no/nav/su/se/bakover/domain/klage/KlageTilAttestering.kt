package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

sealed class KlageTilAttestering : Klage {
    abstract override val id: UUID
    abstract override val opprettet: Tidspunkt
    abstract override val sakId: UUID
    abstract override val saksnummer: Saksnummer
    abstract override val fnr: Fnr
    abstract override val journalpostId: JournalpostId
    abstract override val oppgaveId: OppgaveId
    abstract override val saksbehandler: NavIdentBruker.Saksbehandler
    abstract override val datoKlageMottatt: LocalDate
    abstract override val klagevedtakshistorikk: Klagevedtakshistorikk
    abstract val attesteringer: Attesteringshistorikk
    abstract val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt
    abstract val vurderinger: VurderingerTilKlage?

    data class Avvist private constructor(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val journalpostId: JournalpostId,
        override val oppgaveId: OppgaveId,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val datoKlageMottatt: LocalDate,
        override val attesteringer: Attesteringshistorikk,
        override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
        override val vurderinger: VurderingerTilKlage?,
        override val klagevedtakshistorikk: Klagevedtakshistorikk,
        val fritekstTilBrev: String,
    ) : KlageTilAttestering() {

        override fun avvis(
            iverksattAttestering: Attestering.Iverksatt,
        ): Either<KunneIkkeIverksetteAvvistKlage, IverksattAvvistKlage> {
            if (iverksattAttestering.attestant.navIdent == saksbehandler.navIdent) {
                return KunneIkkeIverksetteAvvistKlage.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
            return IverksattAvvistKlage.create(
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
                attesteringer = attesteringer.leggTilNyAttestering(iverksattAttestering),
                datoKlageMottatt = datoKlageMottatt,
                fritekstTilBrev = fritekstTilBrev
            ).right()
        }

        override fun underkjenn(
            underkjentAttestering: Attestering.Underkjent,
            opprettOppgave: () -> Either<KunneIkkeUnderkjenne.KunneIkkeOppretteOppgave, OppgaveId>,
        ): Either<KunneIkkeUnderkjenne, AvvistKlage.Bekreftet> {
            if (underkjentAttestering.attestant.navIdent == saksbehandler.navIdent) {
                return KunneIkkeUnderkjenne.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
            return opprettOppgave().map { oppgaveId ->
                AvvistKlage.Bekreftet.create(
                    forrigeSteg = VilkårsvurdertKlage.Bekreftet.Avvist.create(
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
                        attesteringer = attesteringer.leggTilNyAttestering(underkjentAttestering),
                        datoKlageMottatt = datoKlageMottatt,
                    ),
                    fritekstTilBrev = this.fritekstTilBrev,
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
                vurderinger: VurderingerTilKlage?,
                attesteringer: Attesteringshistorikk,
                datoKlageMottatt: LocalDate,
                fritekstTilBrev: String,
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
                    datoKlageMottatt = datoKlageMottatt,
                    attesteringer = attesteringer,
                    vilkårsvurderinger = vilkårsvurderinger,
                    vurderinger = vurderinger,
                    fritekstTilBrev = fritekstTilBrev,
                )
            }
        }
    }

    data class Vurdert private constructor(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val saksnummer: Saksnummer,
        override val fnr: Fnr,
        override val journalpostId: JournalpostId,
        override val oppgaveId: OppgaveId,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val datoKlageMottatt: LocalDate,
        override val attesteringer: Attesteringshistorikk,
        override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
        override val vurderinger: VurderingerTilKlage.Utfylt,
        override val klagevedtakshistorikk: Klagevedtakshistorikk,
    ) : KlageTilAttestering() {

        override fun underkjenn(
            underkjentAttestering: Attestering.Underkjent,
            opprettOppgave: () -> Either<KunneIkkeUnderkjenne.KunneIkkeOppretteOppgave, OppgaveId>,
        ): Either<KunneIkkeUnderkjenne, Klage> {
            if (underkjentAttestering.attestant.navIdent == saksbehandler.navIdent) {
                return KunneIkkeUnderkjenne.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
            return opprettOppgave().map { oppgaveId ->
                VurdertKlage.Bekreftet.create(
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
                    attesteringer = attesteringer.leggTilNyAttestering(underkjentAttestering),
                    datoKlageMottatt = datoKlageMottatt,
                klagevedtakshistorikk = klagevedtakshistorikk)
            }
        }

        override fun oversend(
            iverksattAttestering: Attestering.Iverksatt,
        ): Either<KunneIkkeOversendeKlage, OversendtKlage> {
            if (iverksattAttestering.attestant.navIdent == saksbehandler.navIdent) {
                return KunneIkkeOversendeKlage.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
            return OversendtKlage.create(
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
                attesteringer = attesteringer.leggTilNyAttestering(iverksattAttestering),
                datoKlageMottatt = datoKlageMottatt,
            klagevedtakshistorikk = klagevedtakshistorikk).right()
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
            ): Vurdert {
                return Vurdert(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    fnr = fnr,
                    journalpostId = journalpostId,
                    oppgaveId = oppgaveId,
                    saksbehandler = saksbehandler,
                    datoKlageMottatt = datoKlageMottatt,
                    attesteringer = attesteringer,
                    vilkårsvurderinger = vilkårsvurderinger,
                    vurderinger = vurderinger,
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
            fritekstTilBrev: String,
            klagevedtakshistorikk: Klagevedtakshistorikk,
        ): KlageTilAttestering {
            if (VilkårsvurdertKlage.erAvvist(vilkårsvurderinger)) {
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
                    vurderinger = vurderinger,
                    attesteringer = attesteringer,
                    datoKlageMottatt = datoKlageMottatt,
                    fritekstTilBrev = fritekstTilBrev
                        ?: throw IllegalStateException("Avvist klage som er til attestering må ha brev-tekst. id: $id"),
                )
            }
            return Vurdert.create(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                saksnummer = saksnummer,
                fnr = fnr,
                journalpostId = journalpostId,
                oppgaveId = oppgaveId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderinger,
                vurderinger = when (vurderinger) {
                    is VurderingerTilKlage.Utfylt -> vurderinger
                    else -> throw IllegalStateException("Prøvde å lage en klage til attestering (TilVurdering) uten vurderinger. En klage til attestering må ha vurderinger. Id $id")
                },
                attesteringer = attesteringer,
                datoKlageMottatt = datoKlageMottatt,
                klagevedtakshistorikk = klagevedtakshistorikk
            )
        }
    }
}

sealed class KunneIkkeSendeTilAttestering {
    object FantIkkeKlage : KunneIkkeSendeTilAttestering()
    object KunneIkkeOppretteOppgave : KunneIkkeSendeTilAttestering()
    data class UgyldigTilstand(val fra: KClass<out Klage>, val til: KClass<out Klage>) : KunneIkkeSendeTilAttestering()
}

sealed class KunneIkkeUnderkjenne {
    object FantIkkeKlage : KunneIkkeUnderkjenne()
    object KunneIkkeOppretteOppgave : KunneIkkeUnderkjenne()
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeUnderkjenne()
    data class UgyldigTilstand(val fra: KClass<out Klage>, val til: KClass<out Klage>) : KunneIkkeUnderkjenne()
}
