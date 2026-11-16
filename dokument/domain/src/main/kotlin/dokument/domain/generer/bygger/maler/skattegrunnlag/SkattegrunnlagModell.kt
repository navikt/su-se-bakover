package dokument.domain.generer.bygger.maler.skattegrunnlag

import arrow.core.NonEmptyList
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.person.Fnr
import person.domain.Person
import java.time.LocalDate

data class SkattPdfDataJson(
    val fnr: Fnr,
    val navn: Person.Navn,
    val årsgrunnlag: NonEmptyList<SamletÅrsgrunnlagPdfJson>,
)

enum class BehandlingstypeForSkattemelding {
    Søknadsbehandling,
    Frioppslag,
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(
        value = SamletÅrsgrunnlagPdfJson.HarIkkeSkattegrunnlagForÅrOgStadie::class,
        name = "HarIkkeSkattegrunnlag",
    ),
    JsonSubTypes.Type(
        value = SamletÅrsgrunnlagPdfJson.HarSkattegrunnlagForÅrOgStadie::class,
        name = "HarSkattegrunnlag",
    ),
)
sealed interface SamletÅrsgrunnlagPdfJson {
    val år: Int
    val stadie: ÅrsgrunnlagStadie

    data class HarSkattegrunnlagForÅrOgStadie(
        override val år: Int,
        override val stadie: ÅrsgrunnlagStadie,
        val oppgjørsdato: LocalDate?,
        val formue: List<SkattegrunnlagPdfJson.Formue> = emptyList(),
        val inntekt: List<SkattegrunnlagPdfJson.Inntekt> = emptyList(),
        val inntektsfradrag: List<SkattegrunnlagPdfJson.Inntektsfradrag> = emptyList(),
        val formuesfradrag: List<SkattegrunnlagPdfJson.Formuesfradrag> = emptyList(),
        val verdsettingsrabattSomGirGjeldsreduksjon: List<SkattegrunnlagPdfJson.VerdsettingsrabattSomGirGjeldsreduksjon> = emptyList(),
        val oppjusteringAvEierinntekter: List<SkattegrunnlagPdfJson.OppjusteringAvEierinntekter> = emptyList(),
        val annet: List<SkattegrunnlagPdfJson.Annet> = emptyList(),
        val kjøretøy: List<SpesifiseringPdfJson.KjøretøyJson>,
    ) : SamletÅrsgrunnlagPdfJson

    data class HarIkkeSkattegrunnlagForÅrOgStadie(
        override val år: Int,
        override val stadie: ÅrsgrunnlagStadie,
        val grunn: HarIkkeSkattegrunnlagFordi,
    ) : SamletÅrsgrunnlagPdfJson {

        enum class HarIkkeSkattegrunnlagFordi {
            NETTVERKSFEIL,
            FINNES_IKKE,
            UKJENT_FEIL,
            MANGLER_RETTIGHETER,
            OPPSLAGET_INNEHOLDT_UGYLDIG_DATA,
        }
    }
}

enum class ÅrsgrunnlagStadie {
    Oppgjør,
    Utkast,
}

sealed interface SkattegrunnlagPdfJson {
    val tekniskNavn: String
    val beløp: String
    val spesifisering: List<SpesifiseringPdfJson>

    data class Formue(
        override val tekniskNavn: String,
        override val beløp: String,
    ) : SkattegrunnlagPdfJson {
        /**
         * Formue feltet som skal til pdf'en skal ikke ha kjøretøy i seg. Den skal være i et eget felt
         */
        override val spesifisering: List<SpesifiseringPdfJson.KjøretøyJson> = emptyList()
    }

    data class Inntekt(
        override val tekniskNavn: String,
        override val beløp: String,
    ) : SkattegrunnlagPdfJson {
        override val spesifisering: List<SpesifiseringPdfJson> = emptyList()
    }

    data class Inntektsfradrag(
        override val tekniskNavn: String,
        override val beløp: String,
    ) : SkattegrunnlagPdfJson {
        override val spesifisering: List<SpesifiseringPdfJson> = emptyList()
    }

    data class Formuesfradrag(
        override val tekniskNavn: String,
        override val beløp: String,
    ) : SkattegrunnlagPdfJson {
        override val spesifisering: List<SpesifiseringPdfJson> = emptyList()
    }

    data class VerdsettingsrabattSomGirGjeldsreduksjon(
        override val tekniskNavn: String,
        override val beløp: String,
    ) : SkattegrunnlagPdfJson {
        override val spesifisering: List<SpesifiseringPdfJson> = emptyList()
    }

    data class OppjusteringAvEierinntekter(
        override val tekniskNavn: String,
        override val beløp: String,
    ) : SkattegrunnlagPdfJson {
        override val spesifisering: List<SpesifiseringPdfJson> = emptyList()
    }

    data class Annet(
        override val tekniskNavn: String,
        override val beløp: String,
    ) : SkattegrunnlagPdfJson {
        override val spesifisering: List<SpesifiseringPdfJson> = emptyList()
    }
}

sealed interface SpesifiseringPdfJson {
    data class KjøretøyJson(
        val beløp: String? = null,
        val registreringsnummer: String? = null,
        val fabrikatnavn: String? = null,
        val årForFørstegangsregistrering: String? = null,
        val formuesverdi: String? = null,
        val antattVerdiSomNytt: String? = null,
        val antattMarkedsverdi: String? = null,
    ) : SpesifiseringPdfJson
}
