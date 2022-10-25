package no.nav.su.se.bakover.domain.sak

import no.nav.su.se.bakover.common.Tidspunkt
import java.util.UUID

/**
 * @param behandlingStartet Dette skulle egentlig vært sistEndret
 */
data class Behandlingsoversikt(
    val saksnummer: Saksnummer,
    val behandlingsId: UUID,
    val behandlingstype: Behandlingstype,
    val behandlingStartet: Tidspunkt?,
    val status: Behandlingsstatus,
) {

    enum class Behandlingstype {
        SØKNADSBEHANDLING,
        REVURDERING,
        KLAGE,
        ;
    }

    enum class Behandlingsstatus {
        UNDER_BEHANDLING,
        NY_SØKNAD,
        UNDERKJENT,
        TIL_ATTESTERING,
        OPPHØR,
        AVSLAG,
        INGEN_ENDRING,
        INNVILGET,
        STANS,
        GJENOPPTAK,
        OVERSENDT,
        ;
    }
}
