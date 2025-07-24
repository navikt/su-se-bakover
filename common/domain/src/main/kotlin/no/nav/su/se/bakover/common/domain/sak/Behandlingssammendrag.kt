package no.nav.su.se.bakover.common.domain.sak

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall

/**
 * Brukes for å se åpne og ferdige behandlinger i saksoversikten.
 * Per tidspunkt: søknader, søknadsbehandlinger, revurderinger, klager, tilbakekrevinger og kravgrunnlag.
 *
 * @param behandlingStartet Dette skulle egentlig vært sistEndret
 */
data class Behandlingssammendrag(
    val sakType: Sakstype,
    val saksnummer: Saksnummer,
    val periode: DatoIntervall?,
    val behandlingstype: Behandlingstype,
    val behandlingStartet: Tidspunkt?,
    val status: Behandlingsstatus?,
) {

    enum class Behandlingstype {
        SØKNADSBEHANDLING,
        REVURDERING,
        KLAGE,
        OMGJØRING,
        TILBAKEKREVING,
        KRAVGRUNNLAG,
    }

    enum class Behandlingsstatus {
        UNDER_BEHANDLING,
        NY_SØKNAD,
        UNDERKJENT,
        TIL_ATTESTERING,
        OPPHØR,
        AVSLAG,
        INNVILGET,
        STANS,
        GJENOPPTAK,
        OVERSENDT,
        IVERKSATT,
        AVSLUTTET,
        ÅPEN,
        AVBRUTT,
    }
}
