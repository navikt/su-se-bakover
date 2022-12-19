package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Flyktningsstatus

data class FlyktningsstatusJson(
    val registrertFlyktning: Boolean,
) {
    fun toFlyktningsstatus() = Flyktningsstatus(registrertFlyktning)

    companion object {
        fun Flyktningsstatus.toFlyktningsstatusJson() = FlyktningsstatusJson(this.registrertFlyktning)
    }
}
