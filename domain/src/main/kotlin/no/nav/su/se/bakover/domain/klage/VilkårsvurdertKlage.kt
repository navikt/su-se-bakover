package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.journal.JournalpostId
import java.util.UUID

/**
 * TODO jah: Vi støtter ikke avvisning i MVP, men vi må i så fall legge til noe validering på at vi ikke kan ha false på de vurderingene.
 */
sealed class VilkårsvurdertKlage : Klage() {

    abstract val vilkårsvurderinger: VilkårsvurderingerTilKlage

    /**
     * Siden det er mulig å gå tilbake fra [VurdertKlage] til [VilkårsvurdertKlage] må vå holde på den informasjon.
     */
    abstract val vurderinger: VurderingerTilKlage?

    override fun vilkårsvurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vilkårsvurderinger: VilkårsvurderingerTilKlage,
    ): Either<KunneIkkeVilkårsvurdereKlage, VilkårsvurdertKlage> {
        return when (vilkårsvurderinger) {
            is VilkårsvurderingerTilKlage.Utfylt -> Utfylt.create(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                journalpostId = journalpostId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderinger,
            )
            is VilkårsvurderingerTilKlage.Påbegynt -> Påbegynt.create(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                journalpostId = journalpostId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderinger,
            )
        }.right()
    }

    override fun vurder(
        saksbehandler: NavIdentBruker.Saksbehandler,
        vurderinger: VurderingerTilKlage,
    ): Either<KunneIkkeVurdereKlage.UgyldigTilstand, VurdertKlage> {
        return when (val vilkårsvurderinger = vilkårsvurderinger) {
            is VilkårsvurderingerTilKlage.Påbegynt -> KunneIkkeVurdereKlage.UgyldigTilstand(
                Påbegynt::class,
                VurdertKlage::class,
            ).left()
            is VilkårsvurderingerTilKlage.Utfylt -> when (vurderinger) {
                is VurderingerTilKlage.Påbegynt -> VurdertKlage.Påbegynt.create(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    journalpostId = journalpostId,
                    saksbehandler = saksbehandler,
                    vilkårsvurderinger = vilkårsvurderinger,
                    vurderinger = vurderinger,
                )
                is VurderingerTilKlage.Utfylt -> VurdertKlage.Utfylt.create(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    journalpostId = journalpostId,
                    saksbehandler = saksbehandler,
                    vilkårsvurderinger = vilkårsvurderinger,
                    vurderinger = vurderinger,
                )
            }.right()
        }
    }

    data class Påbegynt private constructor(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val journalpostId: JournalpostId,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Påbegynt,
        override val vurderinger: VurderingerTilKlage?,
    ) : VilkårsvurdertKlage() {

        companion object {
            fun create(
                id: UUID,
                opprettet: Tidspunkt,
                sakId: UUID,
                journalpostId: JournalpostId,
                saksbehandler: NavIdentBruker.Saksbehandler,
                vilkårsvurderinger: VilkårsvurderingerTilKlage.Påbegynt,
                vurderinger: VurderingerTilKlage? = null,
            ) = Påbegynt(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                journalpostId = journalpostId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderinger,
                vurderinger = vurderinger,
            )
        }
    }

    data class Utfylt private constructor(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val journalpostId: JournalpostId,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
        override val vurderinger: VurderingerTilKlage?,
    ) : VilkårsvurdertKlage() {

        companion object {
            fun create(
                id: UUID,
                opprettet: Tidspunkt,
                sakId: UUID,
                journalpostId: JournalpostId,
                saksbehandler: NavIdentBruker.Saksbehandler,
                vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
                vurderinger: VurderingerTilKlage? = null,
            ) = Utfylt(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                journalpostId = journalpostId,
                saksbehandler = saksbehandler,
                vilkårsvurderinger = vilkårsvurderinger,
                vurderinger = vurderinger,
            )
        }
    }
}

sealed class KunneIkkeVilkårsvurdereKlage {
    object FantIkkeKlage : KunneIkkeVilkårsvurdereKlage()
    object FantIkkeVedtak : KunneIkkeVilkårsvurdereKlage()
}
