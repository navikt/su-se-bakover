package no.nav.su.se.bakover.domain.søknadinnhold

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.LocalDate

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ForNav.DigitalSøknad::class, name = "DigitalSøknad"),
    JsonSubTypes.Type(value = ForNav.Papirsøknad::class, name = "Papirsøknad"),
)
sealed class ForNav {

    abstract fun erPapirsøknad(): Boolean

    data class DigitalSøknad(
        val harFullmektigEllerVerge: Vergemål? = null,
    ) : ForNav() {
        enum class Vergemål() {
            FULLMEKTIG,
            VERGE,
            ;
        }

        override fun erPapirsøknad(): Boolean {
            return false
        }
    }

    data class Papirsøknad(
        val mottaksdatoForSøknad: LocalDate,
        val grunnForPapirinnsending: GrunnForPapirinnsending,
        val annenGrunn: String?,
    ) : ForNav() {
        override fun erPapirsøknad(): Boolean {
            return true
        }
        enum class GrunnForPapirinnsending() {
            VergeHarSøktPåVegneAvBruker,
            MidlertidigUnntakFraOppmøteplikt,
            Annet,
        }
    }
}
