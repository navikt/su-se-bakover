package no.nav.su.se.bakover.test.avkorting

import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling.IkkeVurdert
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling.IngenAvkorting
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling.SkalAvkortes
import no.nav.su.se.bakover.test.avkortingsvarselAvkortet
import no.nav.su.se.bakover.test.avkortingsvarselSkalAvkortes
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.simulering.simuleringFeilutbetaling
import økonomi.domain.simulering.Simulering
import java.util.UUID

fun avkortingVedSøknadsbehandlingIngenAvkorting() = IngenAvkorting

fun avkortingVedSøknadsbehandlingIkkeVurdert() = IkkeVurdert

fun avkortingVedSøknadsbehandlingSkalAvkortes(
    vararg perioder: Periode = listOf(juni(2021)).toTypedArray(),
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = UUID.randomUUID(),
    revurderingId: UUID = UUID.randomUUID(),
    simulering: Simulering = simuleringFeilutbetaling(
        perioder = perioder,
    ),
) = SkalAvkortes(
    avkortingsvarsel = avkortingsvarselSkalAvkortes(
        perioder = perioder,
        id = id,
        sakId = sakId,
        revurderingId = revurderingId,
        opprettet = opprettet,
        simulering = simulering,
    ),
)

fun avkortingVedSøknadsbehandlingAvkortet(
    vararg perioder: Periode = listOf(juni(2021)).toTypedArray(),
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    sakId: UUID = UUID.randomUUID(),
    revurderingId: UUID = UUID.randomUUID(),
    simulering: Simulering = simuleringFeilutbetaling(
        perioder = perioder,
    ),
    søknadsbehandlingId: UUID = UUID.randomUUID(),
) = AvkortingVedSøknadsbehandling.Avkortet(
    avkortingsvarsel = avkortingsvarselAvkortet(
        perioder = perioder,
        id = id,
        sakId = sakId,
        revurderingId = revurderingId,
        opprettet = opprettet,
        simulering = simulering,
        søknadsbehandlingId = søknadsbehandlingId,
    ),
)
