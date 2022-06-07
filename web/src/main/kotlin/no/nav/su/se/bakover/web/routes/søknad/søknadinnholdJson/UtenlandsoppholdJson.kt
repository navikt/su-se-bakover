package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import no.nav.su.se.bakover.domain.Utenlandsopphold
import no.nav.su.se.bakover.domain.UtenlandsoppholdPeriode
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.UtenlandsoppholdPeriodeJson.Companion.toUtenlandsoppholdJson
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class UtenlandsoppholdJson(
    val registrertePerioder: List<UtenlandsoppholdPeriodeJson>? = null,
    val planlagtePerioder: List<UtenlandsoppholdPeriodeJson>? = null,
) {
    fun toUtenlandsopphold() = Utenlandsopphold(
        registrertePerioder = registrertePerioder.toJson(),
        planlagtePerioder = planlagtePerioder.toJson(),
    )

    fun List<UtenlandsoppholdPeriodeJson>?.toJson() = this?.map { it.toUtenlandsopphold() }

    companion object {

        fun Utenlandsopphold.toUtenlandsoppholdJson() =
            UtenlandsoppholdJson(
                registrertePerioder = this.registrertePerioder.toUtenlandsoppholdPeriodeJsonList(),
                planlagtePerioder = this.planlagtePerioder.toUtenlandsoppholdPeriodeJsonList(),
            )

        fun List<UtenlandsoppholdPeriode>?.toUtenlandsoppholdPeriodeJsonList(): List<UtenlandsoppholdPeriodeJson>? =
            this?.map { it.toUtenlandsoppholdJson() }
    }
}

data class UtenlandsoppholdPeriodeJson(
    val utreisedato: String,
    val innreisedato: String,
) {
    fun toUtenlandsopphold() = UtenlandsoppholdPeriode(
        utreisedato = LocalDate.parse(
            utreisedato,
            DateTimeFormatter.ISO_DATE,
        ),
        innreisedato = LocalDate.parse(
            innreisedato,
            DateTimeFormatter.ISO_DATE,
        ),
    )

    companion object {
        fun UtenlandsoppholdPeriode.toUtenlandsoppholdJson() =
            UtenlandsoppholdPeriodeJson(
                utreisedato = utreisedato.toString(),
                innreisedato = innreisedato.toString(),
            )
    }
}
