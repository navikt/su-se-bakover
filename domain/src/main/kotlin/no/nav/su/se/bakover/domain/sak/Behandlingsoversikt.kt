package no.nav.su.se.bakover.domain.sak

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Saksnummer
import java.util.UUID

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
        KLAGE;
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
        AVSLUTTET;
    }
}
