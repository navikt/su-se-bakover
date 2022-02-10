package no.nav.su.se.bakover.domain.sak

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Saksnummer
import java.util.UUID

// TODO: finn bedre navn på disse greiene
sealed interface SakBehandlinger {
    val saksnummer: Saksnummer
    val behandlingsId: UUID
    val restansType: RestansType
    val behandlingStartet: Tidspunkt?

    enum class RestansType {
        SØKNADSBEHANDLING,
        REVURDERING,
        KLAGE;
    }

    data class FerdigBehandling(
        override val saksnummer: Saksnummer,
        override val behandlingsId: UUID,
        override val restansType: RestansType,
        override val behandlingStartet: Tidspunkt?,
        val result: RestansResultat,
    ) : SakBehandlinger {

        enum class RestansResultat {
            OPPHØR,
            AVSLAG,
            INGEN_ENDRING,
            INNVILGET,
            AVSLUTTET;
        }
    }

    data class ÅpenBehandling(
        override val saksnummer: Saksnummer,
        override val behandlingsId: UUID,
        override val restansType: RestansType,
        override val behandlingStartet: Tidspunkt?,
        val status: RestansStatus,
    ) : SakBehandlinger {

        enum class RestansStatus {
            UNDER_BEHANDLING,
            NY_SØKNAD,
            UNDERKJENT,
            TIL_ATTESTERING;
        }
    }
}
