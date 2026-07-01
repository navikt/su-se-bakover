package no.nav.su.se.bakover.kontrollsamtale.domain

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt

enum class KontrollsamtaleHandling {
    PLANLAGT_INNKALLING,
    INNKALT,
    GJENNOMFØRT,
    ANNULLERT,
    IKKE_MØTT_INNEN_FRIST,
}

enum class KontrollsamtaleHendelseRolle {
    ATTESTANT,
    SAKSBEHANDLER,
    VEILEDER,
    DRIFT,
    ;

    fun toNavIdentBruker(navIdent: String): NavIdentBruker {
        return when (this) {
            ATTESTANT -> NavIdentBruker.Attestant(navIdent)
            SAKSBEHANDLER -> NavIdentBruker.Saksbehandler(navIdent)
            VEILEDER -> NavIdentBruker.Veileder(navIdent)
            DRIFT -> NavIdentBruker.Drift(navIdent)
        }
    }

    companion object {
        fun fromNavIdentBruker(navIdentBruker: NavIdentBruker): KontrollsamtaleHendelseRolle {
            return when (navIdentBruker) {
                is NavIdentBruker.Attestant -> ATTESTANT
                is NavIdentBruker.Saksbehandler -> SAKSBEHANDLER
                is NavIdentBruker.Veileder -> VEILEDER
                is NavIdentBruker.Drift -> DRIFT
            }
        }
    }
}

data class KontrollsamtaleHendelse(
    val navIdent: NavIdentBruker,
    val tidspunkt: Tidspunkt,
    val handling: KontrollsamtaleHandling,
)

fun Kontrollsamtale.leggTilHendelse(hendelse: KontrollsamtaleHendelse): Kontrollsamtale {
    return copy(hendelser = this.hendelser + hendelse)
}

fun Kontrollsamtale.leggTilStatusHendelse(
    utførtAv: NavIdentBruker,
    tidspunkt: Tidspunkt,
): Kontrollsamtale {
    return leggTilHendelse(
        KontrollsamtaleHendelse(
            navIdent = utførtAv,
            tidspunkt = tidspunkt,
            handling = status.toHandling(),
        ),
    )
}

fun Kontrollsamtalestatus.toHandling(): KontrollsamtaleHandling {
    return KontrollsamtaleHandling.valueOf(this.name)
}

fun KontrollsamtaleHendelse.toRolle(): KontrollsamtaleHendelseRolle {
    return KontrollsamtaleHendelseRolle.fromNavIdentBruker(navIdent)
}
