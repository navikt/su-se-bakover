package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpFactory
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.satser.SatsFactoryForSupplerendeStønad
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory

// kommentar jah: Dersom/når satser flyttes ut av kildekoden, f.eks. til databasen, må vi definere satsene for test her. Kan starte med å kopiere [SatsFactoryForSupplerendeStønad]
val satsFactoryTest: SatsFactory = SatsFactoryForSupplerendeStønad()

val fullSupplerendeStønadOrdinærTest = satsFactoryTest.fullSupplerendeStønadOrdinær()

val fullSupplerendeStønadHøyTest = satsFactoryTest.fullSupplerendeStønadHøy()

val formuegrenserFactoryTest: FormuegrenserFactory = satsFactoryTest.formuegrenserFactory

val grunnbeløpFactoryTest: GrunnbeløpFactory = satsFactoryTest.grunnbeløpFactory
