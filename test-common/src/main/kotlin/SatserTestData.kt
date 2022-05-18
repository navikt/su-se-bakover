package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import java.time.Clock

// kommentar jah: Dersom/når satser flyttes ut av kildekoden, f.eks. til databasen, må vi definere satsene for test her. Kan starte med å kopiere [SatsFactoryForSupplerendeStønad]
val satsFactoryTest: SatsFactory = SatsFactoryForSupplerendeStønad(fixedClock)

fun satsFactoryTest(clock: Clock = fixedClock) = SatsFactoryForSupplerendeStønad(clock)

val formuegrenserFactoryTest: FormuegrenserFactory = satsFactoryTest.formuegrenserFactory
