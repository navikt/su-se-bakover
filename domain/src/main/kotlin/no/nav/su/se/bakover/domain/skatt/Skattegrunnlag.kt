package no.nav.su.se.bakover.domain.skatt

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.YearRange
import no.nav.su.se.bakover.common.toYearRange
import java.time.LocalDate

data class Skattegrunnlag(
    val fnr: Fnr,
    val hentetTidspunkt: Tidspunkt,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val årsgrunnlag: NonEmptyList<SamletSkattegrunnlagForÅrOgStadie>,
    val årSpurtFor: YearRange,
) {
    init {
        require(årsgrunnlag.map { it.inntektsår }.toYearRange() == årSpurtFor) {
            "År som er spurt for, er ikke lik det som finnes i årsgrunnlagene"
        }
    }

    constructor(
        fnr: Fnr,
        hentetTidspunkt: Tidspunkt,
        saksbehandler: NavIdentBruker.Saksbehandler,
        årsgrunnlag: SamletSkattegrunnlagForÅrOgStadie,
        årSpurtFor: YearRange,
    ) : this(fnr, hentetTidspunkt, saksbehandler, nonEmptyListOf(årsgrunnlag), årSpurtFor)

    data class SkattegrunnlagForÅr(
        val oppgjørsdato: LocalDate?,
        val formue: List<Grunnlag.Formue> = emptyList(),
        val inntekt: List<Grunnlag.Inntekt> = emptyList(),
        val inntektsfradrag: List<Grunnlag.Inntektsfradrag> = emptyList(),
        val formuesfradrag: List<Grunnlag.Formuesfradrag> = emptyList(),
        val verdsettingsrabattSomGirGjeldsreduksjon: List<Grunnlag.VerdsettingsrabattSomGirGjeldsreduksjon> = emptyList(),
        val oppjusteringAvEierinntekter: List<Grunnlag.OppjusteringAvEierinntekter> = emptyList(),
        val manglerKategori: List<Grunnlag.ManglerKategori> = emptyList(),
        val annet: List<Grunnlag.Annet> = emptyList(),
    )

    /**
     * https://skatteetaten.github.io/datasamarbeid-api-dokumentasjon/data_summertskattegrunnlag2021
     * @property spesifisering Vi får per tidspunkt kun spesifisering av kjøretøy (men dette kan endre seg)
     */
    sealed interface Grunnlag {
        val navn: String
        val beløp: String
        val spesifisering: List<Spesifisering>

        data class Formue(
            override val navn: String,
            override val beløp: String,
            override val spesifisering: List<Spesifisering.Kjøretøy> = emptyList(),
        ) : Grunnlag

        data class Inntekt(
            override val navn: String,
            override val beløp: String,
        ) : Grunnlag {
            override val spesifisering: List<Spesifisering> = emptyList()
        }

        data class Inntektsfradrag(
            override val navn: String,
            override val beløp: String,
        ) : Grunnlag {
            override val spesifisering: List<Spesifisering> = emptyList()
        }

        data class Formuesfradrag(
            override val navn: String,
            override val beløp: String,
        ) : Grunnlag {
            override val spesifisering: List<Spesifisering> = emptyList()
        }

        data class VerdsettingsrabattSomGirGjeldsreduksjon(
            override val navn: String,
            override val beløp: String,
        ) : Grunnlag {
            override val spesifisering: List<Spesifisering> = emptyList()
        }

        data class OppjusteringAvEierinntekter(
            override val navn: String,
            override val beløp: String,
        ) : Grunnlag {
            override val spesifisering: List<Spesifisering> = emptyList()
        }

        data class ManglerKategori(
            override val navn: String,
            override val beløp: String,
        ) : Grunnlag {
            override val spesifisering: List<Spesifisering> = emptyList()
        }

        data class Annet(
            override val navn: String,
            override val beløp: String,
        ) : Grunnlag {
            override val spesifisering: List<Spesifisering> = emptyList()
        }
    }

    sealed interface Spesifisering {
        data class Kjøretøy(
            val beløp: String? = null,
            val registreringsnummer: String? = null,
            val fabrikatnavn: String? = null,
            val årForFørstegangsregistrering: String? = null,
            val formuesverdi: String? = null,
            val antattVerdiSomNytt: String? = null,
            val antattMarkedsverdi: String? = null,
        ) : Spesifisering
    }
}
