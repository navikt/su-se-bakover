package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.ktor.server.application.ApplicationCall
import no.nav.su.se.bakover.common.domain.extensions.enumContains
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.ForNav
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
sealed interface ForNavJson {
    fun toForNav(): Either<UgyldigSøknadsinnholdInputFraJson, ForNav>

    fun identBruker(call: ApplicationCall) = when (this) {
        is DigitalSøknad -> NavIdentBruker.Veileder(call.suUserContext.navIdent)
        is Papirsøknad -> NavIdentBruker.Saksbehandler(call.suUserContext.navIdent)
    }

    data class DigitalSøknad(
        val harFullmektigEllerVerge: String? = null,
    ) : ForNavJson {
        override fun toForNav(): Either<UgyldigSøknadsinnholdInputFraJson, ForNav> {
            val vergemål = harFullmektigEllerVerge?.let {
                vergeMålType(it).getOrElse {
                    return it.left()
                }
            }
            return ForNav.DigitalSøknad(vergemål).right()
        }

        private fun vergeMålType(str: String): Either<UgyldigSøknadsinnholdInputFraJson, ForNav.DigitalSøknad.Vergemål> {
            return when (str) {
                "fullmektig" -> ForNav.DigitalSøknad.Vergemål.FULLMEKTIG.right()
                "verge" -> ForNav.DigitalSøknad.Vergemål.VERGE.right()
                else -> UgyldigSøknadsinnholdInputFraJson(
                    felt = "forNav.harFullmektigEllerVerge",
                    begrunnelse = "Ukjent verdi: $str",
                ).left()
            }
        }
    }

    data class Papirsøknad(
        val mottaksdatoForSøknad: LocalDate,
        val grunnForPapirinnsending: String,
        val annenGrunn: String?,
    ) : ForNavJson {
        override fun toForNav(): Either<UgyldigSøknadsinnholdInputFraJson, ForNav> {
            val grunn = grunn(grunnForPapirinnsending).getOrElse {
                return it.left()
            }
            return ForNav.Papirsøknad(
                mottaksdatoForSøknad,
                grunn,
                annenGrunn,
            ).right()
        }

        private fun grunn(str: String): Either<UgyldigSøknadsinnholdInputFraJson, ForNav.Papirsøknad.GrunnForPapirinnsending> =
            if (enumContains<ForNav.Papirsøknad.GrunnForPapirinnsending>(str)) {
                ForNav.Papirsøknad.GrunnForPapirinnsending.valueOf(str).right()
            } else {
                UgyldigSøknadsinnholdInputFraJson(
                    felt = "forNav.grunnForPapirinnsending",
                    begrunnelse = "Ukjent verdi: $str",
                ).left()
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
