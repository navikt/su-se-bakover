package no.nav.su.se.bakover.domain.klage

import arrow.core.left
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID
import kotlin.reflect.KClass

/**
 Da denne ikke attesteres så lagrer vi ikke ned ferdigtidspunkt som de behandlingene av klage
 men dette lagres heller i [datoklageferdigstilt] som er reservert for klagebehandlinger som ikke attesteres.
 */
data class FerdigstiltOmgjortKlage(
    private val forrigeSteg: VurdertKlage.BekreftetOmgjøring,
    override val klageinstanshendelser: Klageinstanshendelser,
    override val sakstype: Sakstype,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    val datoklageferdigstilt: Tidspunkt,
    val behandlingId: UUID? = null,
) : Klage,
    VurdertKlage.OmgjøringKlageFelter by forrigeSteg {

    override fun erÅpen() = false
    override fun kanAvsluttes() = false

    override fun avslutt(
        saksbehandler: NavIdentBruker.Saksbehandler,
        begrunnelse: String,
        tidspunktAvsluttet: Tidspunkt,
    ) = KunneIkkeAvslutteKlage.UgyldigTilstand(this::class).left()
}

sealed interface KunneIkkeFerdigstilleOmgjøringsKlage {
    data object FantIkkeKlage : KunneIkkeFerdigstilleOmgjøringsKlage
    data object KunneIkkeOppretteOppgave : KunneIkkeFerdigstilleOmgjøringsKlage
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeFerdigstilleOmgjøringsKlage {
        val til = FerdigstiltOmgjortKlage::class
    }
}
