package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.september

/** 2021-01-01 - 2021-12-31 */
val periode2021 = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021))

val periodeJanuar2021 = Periode.create(1.januar(2021), 31.januar(2021))
val periodeFebruar2021 = Periode.create(1.februar(2021), 28.februar(2021))
val periodeMars2021 = Periode.create(1.mars(2021), 31.mars(2021))
val periodeApril2021 = Periode.create(1.april(2021), 30.april(2021))
val periodeMai2021 = Periode.create(1.mai(2021), 31.mai(2021))
val periodeJuni2021 = Periode.create(1.juni(2021), 30.juni(2021))
val periodeJuli2021 = Periode.create(1.juli(2021), 31.juli(2021))
val periodeAugust2021 = Periode.create(1.august(2021), 31.august(2021))
val periodeSeptember2021 = Periode.create(1.september(2021), 30.september(2021))
val periodeOktober2021 = Periode.create(1.oktober(2021), 31.oktober(2021))
val periodeNovember2021 = Periode.create(1.november(2021), 30.november(2021))
val periodeDesember2021 = Periode.create(1.desember(2021), 31.desember(2021))
val periodeFørGeregulering2021 = Periode.create(1.januar(2021), 30.april(2021))
val periodeEtterGeregulering2021 = Periode.create(1.mai(2021), 31.desember(2021))
