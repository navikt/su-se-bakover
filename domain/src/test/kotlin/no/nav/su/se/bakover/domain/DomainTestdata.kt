package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import java.time.Clock
import java.time.ZoneOffset

internal val fixedClock: Clock =
    Clock.fixed(1.januar(2021).atTime(1, 2, 3, 456789000).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
internal val fixedTidspunkt: Tidspunkt = Tidspunkt.now(fixedClock)
