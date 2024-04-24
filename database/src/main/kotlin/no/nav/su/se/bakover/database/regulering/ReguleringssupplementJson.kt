package no.nav.su.se.bakover.database.regulering

import no.nav.su.se.bakover.database.regulering.ReguleringssupplementForJson.Companion.toDbJson
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement

internal fun Reguleringssupplement.toDbJson(): List<ReguleringssupplementForJson> = this.map { it.toDbJson() }
