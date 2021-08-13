package no.nav.su.se.bakover.domain.sak

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Saksnummer
import java.util.UUID

data class SakRestans(
    val saksnummer: Saksnummer,
    val behandlingsId: UUID,
    val restansType: RestansType,
    val status: RestansStatus,
    val behandlingStartet: Tidspunkt?,
) {
    enum class RestansType {
        SØKNADSBEHANDLING,
        REVURDERING
    }

    enum class RestansStatus {
        UNDER_BEHANDLING,
        NY_SØKNAD,
        UNDERKJENT,
        TIL_ATTESTERING;
    }
}
