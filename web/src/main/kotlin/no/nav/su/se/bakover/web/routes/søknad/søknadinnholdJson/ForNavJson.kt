package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.ktor.server.application.ApplicationCall
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.enumContains
import no.nav.su.se.bakover.domain.søknadinnhold.ForNav
import no.nav.su.se.bakover.web.features.suUserContext
import java.time.LocalDate

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ForNavJson.DigitalSøknad::class, name = "DigitalSøknad"),
    JsonSubTypes.Type(value = ForNavJson.Papirsøknad::class, name = "Papirsøknad"),
)
sealed class ForNavJson {
    abstract fun toForNav(): ForNav

    fun identBruker(call: ApplicationCall) = when (this) {
        is DigitalSøknad -> NavIdentBruker.Veileder(call.suUserContext.navIdent)
        is Papirsøknad -> NavIdentBruker.Saksbehandler(call.suUserContext.navIdent)
    }

    data class DigitalSøknad(
        val harFullmektigEllerVerge: String? = null,
    ) : ForNavJson() {
        override fun toForNav() = ForNav.DigitalSøknad(
            harFullmektigEllerVerge?.let {
                vergeMålType(it)
            },
        )

        private fun vergeMålType(str: String): ForNav.DigitalSøknad.Vergemål {
            return when (str) {
                "fullmektig" -> ForNav.DigitalSøknad.Vergemål.FULLMEKTIG
                "verge" -> ForNav.DigitalSøknad.Vergemål.VERGE
                else -> throw IllegalArgumentException("Vergemål er ugyldig")
            }
        }
    }

    data class Papirsøknad(
        val mottaksdatoForSøknad: LocalDate,
        val grunnForPapirinnsending: String,
        val annenGrunn: String?,
    ) : ForNavJson() {
        override fun toForNav() = ForNav.Papirsøknad(
            mottaksdatoForSøknad,
            grunn(grunnForPapirinnsending),
            annenGrunn,
        )

        private fun grunn(str: String): ForNav.Papirsøknad.GrunnForPapirinnsending =
            if (enumContains<ForNav.Papirsøknad.GrunnForPapirinnsending>(str)) {
                ForNav.Papirsøknad.GrunnForPapirinnsending.valueOf(str)
            } else {
                throw IllegalArgumentException("Ugyldig grunn")
            }
    }

    companion object {
        fun ForNav.toForNavJson() =
            when (this) {
                is ForNav.DigitalSøknad ->
                    DigitalSøknad(this.harFullmektigEllerVerge?.toJson())
                is ForNav.Papirsøknad ->
                    Papirsøknad(
                        mottaksdatoForSøknad,
                        grunnForPapirinnsending.toString(),
                        annenGrunn,
                    )
            }
    }
}

private fun ForNav.DigitalSøknad.Vergemål.toJson(): String {
    return when (this) {
        ForNav.DigitalSøknad.Vergemål.VERGE -> "verge"
        ForNav.DigitalSøknad.Vergemål.FULLMEKTIG -> "fullmektig"
    }
}
