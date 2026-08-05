package no.nav.su.se.bakover.kontrollsamtale.infrastructure.persistence

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleHandling
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleHendelse
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleHendelseRolle
import no.nav.su.se.bakover.kontrollsamtale.domain.toRolle

internal data class KontrollsamtaleHendelseJson(
    val tidspunkt: Tidspunkt,
    val navIdent: String,
    val handling: String,
    val rolle: String? = null,
) {
    fun toDomain(): KontrollsamtaleHendelse {
        return KontrollsamtaleHendelse(
            navIdent = (rolle?.let { KontrollsamtaleHendelseRolle.valueOf(it) } ?: KontrollsamtaleHendelseRolle.SAKSBEHANDLER)
                .toNavIdentBruker(navIdent),
            tidspunkt = tidspunkt,
            handling = KontrollsamtaleHandling.valueOf(handling),
        )
    }

    companion object {
        fun List<KontrollsamtaleHendelse>.toJson(): List<KontrollsamtaleHendelseJson> = map {
            KontrollsamtaleHendelseJson(
                tidspunkt = it.tidspunkt,
                navIdent = it.navIdent.navIdent,
                handling = it.handling.name,
                rolle = it.toRolle().name,
            )
        }
    }
}
