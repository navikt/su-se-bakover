package no.nav.su.se.bakover.domain.sak

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import java.util.UUID

/**
 * @param behandlingStartet Dette skulle egentlig vært sistEndret
 */
data class Behandlingssammendrag(
    val saksnummer: Saksnummer,
    val behandlingsId: UUID,
    val periode: Periode?,
    val behandlingstype: Behandlingstype,
    val behandlingStartet: Tidspunkt?,
    val status: Behandlingsstatus,
) {

    enum class Behandlingstype {
        SØKNADSBEHANDLING,
        REVURDERING,
        KLAGE,
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
    }
}
