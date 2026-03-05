package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.Utenlandsopphold
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.UtenlandsoppholdPeriode
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.UtenlandsoppholdPeriodeJson.Companion.toUtenlandsoppholdJson
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class UtenlandsoppholdJson(
    val registrertePerioder: List<UtenlandsoppholdPeriodeJson>? = null,
    val planlagtePerioder: List<UtenlandsoppholdPeriodeJson>? = null,
) {
    fun toUtenlandsopphold(): Either<UgyldigSøknadsinnholdInputFraJson, Utenlandsopphold> {
        val registrerte = registrertePerioder.toPerioder(
            feltPrefix = "utenlandsopphold.registrertePerioder",
        ).getOrElse {
            return it.left()
        }
        val planlagte = planlagtePerioder.toPerioder(
            feltPrefix = "utenlandsopphold.planlagtePerioder",
        ).getOrElse {
            return it.left()
        }

        return Utenlandsopphold(
            registrertePerioder = registrerte,
            planlagtePerioder = planlagte,
        ).right()
    }

    private fun List<UtenlandsoppholdPeriodeJson>?.toPerioder(
        feltPrefix: String,
    ): Either<UgyldigSøknadsinnholdInputFraJson, List<UtenlandsoppholdPeriode>?> {
        if (this == null) return null.right()

        val perioder = mutableListOf<UtenlandsoppholdPeriode>()
        forEachIndexed { index, periode ->
            val mappedPeriode = periode.toUtenlandsopphold(
                feltPrefix = feltPrefix,
                index = index,
            ).getOrElse {
                return it.left()
            }
            perioder.add(mappedPeriode)
        }

        return perioder.right()
    }

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
    fun toUtenlandsopphold(
        feltPrefix: String,
        index: Int,
    ): Either<UgyldigSøknadsinnholdInputFraJson, UtenlandsoppholdPeriode> {
        val utreisedato = parseDato(
            verdi = utreisedato,
            felt = "$feltPrefix.$index.utreisedato",
        ).getOrElse {
            return it.left()
        }
        val innreisedato = parseDato(
            verdi = innreisedato,
            felt = "$feltPrefix.$index.innreisedato",
        ).getOrElse {
            return it.left()
        }

        return UtenlandsoppholdPeriode(
            utreisedato = utreisedato,
            innreisedato = innreisedato,
        ).right()
    }

    private fun parseDato(
        verdi: String,
        felt: String,
    ): Either<UgyldigSøknadsinnholdInputFraJson, LocalDate> =
        try {
            LocalDate.parse(
                verdi,
                DateTimeFormatter.ISO_DATE,
            ).right()
        } catch (_: DateTimeParseException) {
            UgyldigSøknadsinnholdInputFraJson(
                felt = felt,
                begrunnelse = "ugyldig datoformat",
            ).left()
        }

    companion object {
        fun UtenlandsoppholdPeriode.toUtenlandsoppholdJson() =
            UtenlandsoppholdPeriodeJson(
                utreisedato = utreisedato.toString(),
                innreisedato = innreisedato.toString(),
            )
    }
}
