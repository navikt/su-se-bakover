package no.nav.su.se.bakover.service

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

internal val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)
internal val fixedTidspunkt: Tidspunkt = Tidspunkt.now(fixedClock)
internal val fixedLocalDate: LocalDate = LocalDate.now(fixedClock)
