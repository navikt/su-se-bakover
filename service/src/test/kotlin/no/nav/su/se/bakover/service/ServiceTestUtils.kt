package no.nav.su.se.bakover.service

import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.startOfDay
import java.time.Clock
import java.time.ZoneOffset

internal val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)
