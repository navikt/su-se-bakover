package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.november
import no.nav.su.se.bakover.common.periode.oktober
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.util.UUID

fun avkortingsvarselUtenlandsopphold(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = UUID.randomUUID(),
    revurderingId: UUID = UUID.randomUUID(),
    simulering: Simulering = simuleringFeilutbetaling(
        oktober(2020),
        november(2020),
        desember(2020),
    ),
) = Avkortingsvarsel.Utenlandsopphold.Opprettet(
    id = id,
    opprettet = opprettet,
    sakId = sakId,
    revurderingId = revurderingId,
    simulering = simulering,
)
