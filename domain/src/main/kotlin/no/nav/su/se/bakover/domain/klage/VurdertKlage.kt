package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.journal.JournalpostId
import java.util.UUID
import kotlin.reflect.KClass

sealed class VurdertKlage : Klage() {

    abstract val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt
    abstract val vurderinger: VurderingerTilKlage
    abstract val attesteringer: Attesteringshistorikk

    /**
     * Dersom vi allerede har vurderinger vil vi ta vare på disse videre.
     */
    override fun vilkårsvurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkårsvurderinger: VilkårsvurderingerTilKlage,
    ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
        return when (vilkårsvurderinger) {
            is VilkårsvurderingerTilKlage.Påbegynt -> VilkårsvurdertKlage.Påbegynt.create(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                journalpostId = journalpostId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderinger,
                vurderinger = vurderinger,
                attesteringer = attesteringer,
            )
            is VilkårsvurderingerTilKlage.Utfylt -> VilkårsvurdertKlage.Utfylt.create(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                journalpostId = journalpostId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderinger,
                vurderinger = vurderinger,
                attesteringer = attesteringer,
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
                journalpostId = journalpostId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderinger,
                vurderinger = vurderinger,
                attesteringshistorikk = attesteringer,
            )
            is VurderingerTilKlage.Utfylt -> Utfylt.create(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                journalpostId = journalpostId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderinger,
                vurderinger = vurderinger,
                attesteringshistorikk = attesteringer,
            )
        }.right()
    }

    data class Påbegynt private constructor(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val journalpostId: JournalpostId,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
        override val vurderinger: VurderingerTilKlage,
        override val attesteringer: Attesteringshistorikk,
    ) : VurdertKlage() {
        companion object {

            fun create(
                id: UUID,
                opprettet: Tidspunkt,
                sakId: UUID,
                journalpostId: JournalpostId,
                saksbehandler: NavIdentBruker.Saksbehandler,
                vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
                vurderinger: VurderingerTilKlage,
                attesteringshistorikk: Attesteringshistorikk,
            ): Påbegynt {
                return Påbegynt(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    journalpostId = journalpostId,
                    saksbehandler = saksbehandler,
                    vilkårsvurderinger = vilkårsvurderinger,
                    vurderinger = vurderinger,
                    attesteringer = attesteringshistorikk,
                )
            }
        }
    }

    data class Utfylt private constructor(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val journalpostId: JournalpostId,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
        override val vurderinger: VurderingerTilKlage.Utfylt,
        override val attesteringer: Attesteringshistorikk,
    ) : VurdertKlage() {

        fun bekreft(): Bekreftet {
            return Bekreftet.create(
                id = this.id,
                opprettet = this.opprettet,
                sakId = this.sakId,
                journalpostId = this.journalpostId,
                saksbehandler = this.saksbehandler,
                vilkårsvurderinger = this.vilkårsvurderinger,
                vurderinger = this.vurderinger,
                attesteringer,
            )
        }

        companion object {
            fun create(
                id: UUID,
                opprettet: Tidspunkt,
                sakId: UUID,
                journalpostId: JournalpostId,
                saksbehandler: NavIdentBruker.Saksbehandler,
                vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
                vurderinger: VurderingerTilKlage.Utfylt,
                attesteringshistorikk: Attesteringshistorikk,
            ): Utfylt {
                return Utfylt(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    journalpostId = journalpostId,
                    saksbehandler = saksbehandler,
                    vilkårsvurderinger = vilkårsvurderinger,
                    vurderinger = vurderinger,
                    attesteringer = attesteringshistorikk,
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
        override val journalpostId: JournalpostId,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
        override val vurderinger: VurderingerTilKlage.Utfylt,
        override val attesteringer: Attesteringshistorikk,
    ) : VurdertKlage() {

        fun sendTilAttestering(): Either<KunneIkkeSendeTilAttestering, KlageTilAttestering> {
            return KlageTilAttestering.create(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                journalpostId = journalpostId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderinger,
                vurderinger = vurderinger,
                attesteringer = attesteringer,
            ).right()
        }

        companion object {
            fun create(
                id: UUID,
                opprettet: Tidspunkt,
                sakId: UUID,
                journalpostId: JournalpostId,
                saksbehandler: NavIdentBruker.Saksbehandler,
                vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
                vurderinger: VurderingerTilKlage.Utfylt,
                attesteringshistorikk: Attesteringshistorikk,
            ): Bekreftet {
                return Bekreftet(
                    id,
                    opprettet,
                    sakId,
                    journalpostId,
                    saksbehandler,
                    vilkårsvurderinger,
                    vurderinger,
                    attesteringshistorikk,
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
