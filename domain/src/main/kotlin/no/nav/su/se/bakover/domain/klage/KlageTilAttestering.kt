package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.journal.JournalpostId
import java.util.UUID
import kotlin.reflect.KClass

data class KlageTilAttestering private constructor(
    override val id: UUID,
    override val opprettet: Tidspunkt,
    override val sakId: UUID,
    override val journalpostId: JournalpostId,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    val attesteringer: Attesteringshistorikk,
    val vilkårsvurderinger: VilkårsvurderingerTilKlage.Utfylt,
    val vurderinger: VurderingerTilKlage.Utfylt,
) : Klage() {

    fun underkjenn(underkjentAttestering: Attestering.Underkjent): Either<KunneIkkeUnderkjenne, VurdertKlage.Bekreftet> {
        return VurdertKlage.Bekreftet.create(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            journalpostId = journalpostId,
            saksbehandler = saksbehandler,
            vilkårsvurderinger = vilkårsvurderinger,
            vurderinger = vurderinger,
            attesteringshistorikk = attesteringer.leggTilNyAttestering(underkjentAttestering),
        ).right()
    }

    override fun iverksett(
        iverksattAttestering: Attestering.Iverksatt
    ): Either<KunneIkkeIverksetteKlage.UgyldigTilstand, IverksattKlage> {
        return IverksattKlage.create(
            id = id,
            opprettet = opprettet,
            sakId = sakId,
            journalpostId = journalpostId,
            saksbehandler = saksbehandler,
            vilkårsvurderinger = vilkårsvurderinger,
            vurderinger = vurderinger,
            attesteringer = attesteringer.leggTilNyAttestering(iverksattAttestering),
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
            attesteringer: Attesteringshistorikk,
        ): KlageTilAttestering {
            return KlageTilAttestering(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                journalpostId = journalpostId,
                saksbehandler = saksbehandler,
                attesteringer = attesteringer,
                vilkårsvurderinger = vilkårsvurderinger,
                vurderinger = vurderinger,
            )
        }
    }
}

sealed class KunneIkkeSendeTilAttestering {
    object FantIkkeKlage : KunneIkkeSendeTilAttestering()
    data class UgyldigTilstand(val fra: KClass<out Klage>, val til: KClass<out Klage>) : KunneIkkeSendeTilAttestering()
}

sealed class KunneIkkeUnderkjenne {
    object FantIkkeKlage : KunneIkkeUnderkjenne()
    object UgyldigGrunn : KunneIkkeUnderkjenne()
    data class UgyldigTilstand(val fra: KClass<out Klage>, val til: KClass<out Klage>) : KunneIkkeUnderkjenne()
}
