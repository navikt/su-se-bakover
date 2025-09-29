package no.nav.su.se.bakover.domain.klage

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.klage.VurdertKlage.Utfylt
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

data class FerdigstiltOmgjortKlage(
    private val forrigeSteg: Utfylt,
    override val klageinstanshendelser: Klageinstanshendelser,
    override val sakstype: Sakstype,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    val behandlingId: UUID? = null,
    val datoklageferdigstilt: LocalDate? = null,
) : Klage,
    VurdertKlage.UtfyltFelter by forrigeSteg {
    override fun erÅpen() = false
    override fun kanAvsluttes() = false

    override fun avslutt(
        saksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String,
        tidspunktAvsluttet: Tidspunkt,
    ) = KunneIkkeAvslutteKlage.UgyldigTilstand(this::class).left()

    override fun ferdigstillOmgjøring(
        saksbehandler: NavIdentBruker.Saksbehandler,
        klage: VurdertKlage.Bekreftet,
    ): Either<KunneIkkeFerdigstilleOmgjøringsKlage, FerdigstiltOmgjortKlage> {
        return FerdigstiltOmgjortKlage(
            forrigeSteg = forrigeSteg,
            saksbehandler = saksbehandler,
            sakstype = sakstype,
            klageinstanshendelser = klage.klageinstanshendelser,
        ).right()
    }
}

sealed interface KunneIkkeFerdigstilleOmgjøringsKlage {
    data object FantIkkeKlage : KunneIkkeFerdigstilleOmgjøringsKlage
    data object KunneIkkeOppretteOppgave : KunneIkkeFerdigstilleOmgjøringsKlage
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeFerdigstilleOmgjøringsKlage {
        val til = FerdigstiltOmgjortKlage::class
    }
}
