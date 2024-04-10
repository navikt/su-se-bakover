package no.nav.su.se.bakover.database.skatt

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.database.common.YearRangeJson
import no.nav.su.se.bakover.database.skatt.GrunnlagDbJson.Annet.Companion.toDomain
import no.nav.su.se.bakover.database.skatt.GrunnlagDbJson.Formue.Companion.toDomain
import no.nav.su.se.bakover.database.skatt.GrunnlagDbJson.Formuesfradrag.Companion.toDomain
import no.nav.su.se.bakover.database.skatt.GrunnlagDbJson.Inntekt.Companion.toDomain
import no.nav.su.se.bakover.database.skatt.GrunnlagDbJson.Inntektsfradrag.Companion.toDomain
import no.nav.su.se.bakover.database.skatt.GrunnlagDbJson.ManglerKategori.Companion.toDomain
import no.nav.su.se.bakover.database.skatt.GrunnlagDbJson.OppjusteringAvEierinntekter.Companion.toDomain
import no.nav.su.se.bakover.database.skatt.GrunnlagDbJson.VerdsettingsrabattSomGirGjeldsreduksjon.Companion.toDomain
import no.nav.su.se.bakover.database.skatt.GrunnlagslisteJson.Companion.toDbJson
import no.nav.su.se.bakover.database.skatt.SpesifiseringDbJson.KjøretøyJson.Companion.toDbJson
import no.nav.su.se.bakover.database.skatt.SpesifiseringDbJson.KjøretøyJson.Companion.toSpesifiseringKjøretøy
import no.nav.su.se.bakover.database.skatt.ÅrsgrunnlagDbJson.Companion.toDbJson
import vilkår.skatt.domain.KunneIkkeHenteSkattemelding
import vilkår.skatt.domain.SamletSkattegrunnlagForÅrOgStadie
import vilkår.skatt.domain.Skattegrunnlag
import java.time.LocalDate
import java.time.Year
import java.util.UUID

internal data class SkattegrunnlagDbJson(
    val årsgrunnlag: List<ÅrsgrunnlagDbJson>,
) {
    fun toSkattegrunnlag(
        id: UUID,
        fnr: String,
        hentetTidspunkt: Tidspunkt,
        saksbehandler: String,
        årSpurtFor: String,
    ): Skattegrunnlag = Skattegrunnlag(
        id = id,
        fnr = Fnr.tryCreate(fnr)!!,
        hentetTidspunkt = hentetTidspunkt,
        saksbehandler = NavIdentBruker.Saksbehandler(saksbehandler),
        årsgrunnlag = årsgrunnlag.map { it.toDomain() }.toNonEmptyList(),
        årSpurtFor = YearRangeJson.toYearRange(årSpurtFor),
    )

    companion object {
        internal fun Skattegrunnlag.toDbJson(): String = SkattegrunnlagDbJson(
            årsgrunnlag = this.årsgrunnlag.map { it.toDbJson() },
        ).let { serialize(it) }

        fun toSkattegrunnlag(
            id: UUID,
            årsgrunnlagJson: String,
            fnr: String,
            hentetTidspunkt: Tidspunkt,
            saksbehandler: String,
            årSpurtFor: String,
        ): Skattegrunnlag = deserialize<SkattegrunnlagDbJson>(årsgrunnlagJson).toSkattegrunnlag(
            id = id,
            fnr = fnr,
            hentetTidspunkt = hentetTidspunkt,
            saksbehandler = saksbehandler,
            årSpurtFor = årSpurtFor,
        )
    }
}

data class ÅrsgrunnlagDbJson(
    val inntektsår: Int,
    val utkast: StadieJson?,
    val oppgjør: StadieJson?,
) {
    init {
        require((utkast == null) xor (oppgjør == null)) {
            "Kan kun persistere en av utkast og oppgjør"
        }
    }

    fun toDomain(): SamletSkattegrunnlagForÅrOgStadie {
        if (oppgjør != null) {
            return SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                oppslag = oppgjør.toDomain(),
                inntektsår = Year.of(inntektsår),
            )
        }

        return SamletSkattegrunnlagForÅrOgStadie.Utkast(
            oppslag = utkast!!.toDomain(),
            inntektsår = Year.of(inntektsår),
        )
    }

    companion object {
        fun SamletSkattegrunnlagForÅrOgStadie.toDbJson(): ÅrsgrunnlagDbJson {
            val stadieJson = when (val o = this.oppslag) {
                is Either.Left -> o.value.toDbJson()
                is Either.Right -> o.value.toDbJson().let {
                    StadieJson.Grunnlag(it, o.value.oppgjørsdato?.toString())
                }
            }
            return ÅrsgrunnlagDbJson(
                inntektsår = this.inntektsår.value,
                oppgjør = if (this is SamletSkattegrunnlagForÅrOgStadie.Oppgjør) stadieJson else null,
                utkast = if (this is SamletSkattegrunnlagForÅrOgStadie.Utkast) stadieJson else null,
            )
        }
    }
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = StadieJson.Grunnlag::class, name = "Grunnlag"),
    JsonSubTypes.Type(value = StadieJson.FinnesIkke::class, name = "FinnesIkke"),
    JsonSubTypes.Type(value = StadieJson.ManglerRettigheter::class, name = "ManglerRettigheter"),
    JsonSubTypes.Type(value = StadieJson.Nettverksfeil::class, name = "Nettverksfeil"),
    JsonSubTypes.Type(value = StadieJson.UkjentFeil::class, name = "UkjentFeil"),
)
sealed interface StadieJson {
    data class Grunnlag(val grunnlag: GrunnlagslisteJson, val oppgjørsdato: String?) : StadieJson
    data object FinnesIkke : StadieJson
    data object ManglerRettigheter : StadieJson
    data object Nettverksfeil : StadieJson
    data object UkjentFeil : StadieJson
    data object OppsalgetInneholdtUgyldigData : StadieJson

    fun toDomain(): Either<KunneIkkeHenteSkattemelding, Skattegrunnlag.SkattegrunnlagForÅr> {
        // Her må man ha is fordi objekt referanser vil endre seg når man henter ut fra db
        return when (this) {
            is FinnesIkke -> KunneIkkeHenteSkattemelding.FinnesIkke.left()
            is Grunnlag -> Skattegrunnlag.SkattegrunnlagForÅr(
                oppgjørsdato = this.oppgjørsdato?.let { LocalDate.parse(it) },
                formue = this.grunnlag.formue.toDomain(),
                inntekt = this.grunnlag.inntekt.toDomain(),
                inntektsfradrag = this.grunnlag.inntektsfradrag.toDomain(),
                formuesfradrag = this.grunnlag.formuesfradrag.toDomain(),
                verdsettingsrabattSomGirGjeldsreduksjon = this.grunnlag.verdsettingsrabattSomGirGjeldsreduksjon.toDomain(),
                oppjusteringAvEierinntekter = this.grunnlag.oppjusteringAvEierinntekter.toDomain(),
                manglerKategori = this.grunnlag.manglerKategori.toDomain(),
                annet = this.grunnlag.annet.toDomain(),
            ).right()

            is ManglerRettigheter -> KunneIkkeHenteSkattemelding.ManglerRettigheter.left()
            is Nettverksfeil -> KunneIkkeHenteSkattemelding.Nettverksfeil.left()
            is UkjentFeil -> KunneIkkeHenteSkattemelding.UkjentFeil.left()
            OppsalgetInneholdtUgyldigData -> KunneIkkeHenteSkattemelding.OppslagetInneholdtUgyldigData.left()
        }
    }
}

data class GrunnlagslisteJson(
    val formue: List<GrunnlagDbJson.Formue>,
    val inntekt: List<GrunnlagDbJson.Inntekt>,
    val inntektsfradrag: List<GrunnlagDbJson.Inntektsfradrag>,
    val formuesfradrag: List<GrunnlagDbJson.Formuesfradrag>,
    val verdsettingsrabattSomGirGjeldsreduksjon: List<GrunnlagDbJson.VerdsettingsrabattSomGirGjeldsreduksjon>,
    val oppjusteringAvEierinntekter: List<GrunnlagDbJson.OppjusteringAvEierinntekter>,
    val manglerKategori: List<GrunnlagDbJson.ManglerKategori>,
    val annet: List<GrunnlagDbJson.Annet>,
) {
    companion object {
        fun Skattegrunnlag.SkattegrunnlagForÅr.toDbJson(): GrunnlagslisteJson = GrunnlagslisteJson(
            formue = this.formue.map {
                GrunnlagDbJson.Formue(navn = it.navn, beløp = it.beløp, spesifisering = it.spesifisering.toDbJson())
            },
            inntekt = this.inntekt.map {
                GrunnlagDbJson.Inntekt(navn = it.navn, beløp = it.beløp)
            },
            inntektsfradrag = this.inntektsfradrag.map {
                GrunnlagDbJson.Inntektsfradrag(navn = it.navn, beløp = it.beløp)
            },
            formuesfradrag = this.formuesfradrag.map {
                GrunnlagDbJson.Formuesfradrag(navn = it.navn, beløp = it.beløp)
            },
            verdsettingsrabattSomGirGjeldsreduksjon = this.verdsettingsrabattSomGirGjeldsreduksjon.map {
                GrunnlagDbJson.VerdsettingsrabattSomGirGjeldsreduksjon(navn = it.navn, beløp = it.beløp)
            },
            oppjusteringAvEierinntekter = this.oppjusteringAvEierinntekter.map {
                GrunnlagDbJson.OppjusteringAvEierinntekter(navn = it.navn, beløp = it.beløp)
            },
            manglerKategori = this.manglerKategori.map {
                GrunnlagDbJson.ManglerKategori(navn = it.navn, beløp = it.beløp)
            },
            annet = this.annet.map {
                GrunnlagDbJson.Annet(navn = it.navn, beløp = it.beløp)
            },
        )
    }
}

sealed interface GrunnlagDbJson {
    val navn: String
    val beløp: String
    val spesifisering: List<SpesifiseringDbJson>

    fun toGrunnlag(): Skattegrunnlag.Grunnlag

    data class Formue(
        override val navn: String,
        override val beløp: String,
        override val spesifisering: List<SpesifiseringDbJson.KjøretøyJson> = emptyList(),
    ) : GrunnlagDbJson {
        override fun toGrunnlag(): Skattegrunnlag.Grunnlag.Formue = Skattegrunnlag.Grunnlag.Formue(
            navn = navn,
            beløp = beløp,
            spesifisering = spesifisering.toSpesifiseringKjøretøy(),
        )

        companion object {
            fun List<Formue>.toDomain(): List<Skattegrunnlag.Grunnlag.Formue> = this.map { it.toGrunnlag() }
        }
    }

    data class Inntekt(
        override val navn: String,
        override val beløp: String,
    ) : GrunnlagDbJson {
        override val spesifisering: List<SpesifiseringDbJson> = emptyList()
        override fun toGrunnlag(): Skattegrunnlag.Grunnlag.Inntekt = Skattegrunnlag.Grunnlag.Inntekt(
            navn = navn,
            beløp = beløp,
        )

        companion object {
            fun List<Inntekt>.toDomain(): List<Skattegrunnlag.Grunnlag.Inntekt> = this.map { it.toGrunnlag() }
        }
    }

    data class Inntektsfradrag(
        override val navn: String,
        override val beløp: String,
    ) : GrunnlagDbJson {
        override val spesifisering: List<SpesifiseringDbJson> = emptyList()
        override fun toGrunnlag(): Skattegrunnlag.Grunnlag.Inntektsfradrag = Skattegrunnlag.Grunnlag.Inntektsfradrag(
            navn = navn,
            beløp = beløp,
        )

        companion object {
            fun List<Inntektsfradrag>.toDomain(): List<Skattegrunnlag.Grunnlag.Inntektsfradrag> =
                this.map { it.toGrunnlag() }
        }
    }

    data class Formuesfradrag(
        override val navn: String,
        override val beløp: String,
    ) : GrunnlagDbJson {
        override val spesifisering: List<SpesifiseringDbJson> = emptyList()
        override fun toGrunnlag(): Skattegrunnlag.Grunnlag.Formuesfradrag = Skattegrunnlag.Grunnlag.Formuesfradrag(
            navn = navn,
            beløp = beløp,
        )

        companion object {
            fun List<Formuesfradrag>.toDomain(): List<Skattegrunnlag.Grunnlag.Formuesfradrag> =
                this.map { it.toGrunnlag() }
        }
    }

    data class VerdsettingsrabattSomGirGjeldsreduksjon(
        override val navn: String,
        override val beløp: String,
    ) : GrunnlagDbJson {
        override val spesifisering: List<SpesifiseringDbJson> = emptyList()
        override fun toGrunnlag(): Skattegrunnlag.Grunnlag.VerdsettingsrabattSomGirGjeldsreduksjon =
            Skattegrunnlag.Grunnlag.VerdsettingsrabattSomGirGjeldsreduksjon(
                navn = navn,
                beløp = beløp,
            )

        companion object {
            fun List<VerdsettingsrabattSomGirGjeldsreduksjon>.toDomain(): List<Skattegrunnlag.Grunnlag.VerdsettingsrabattSomGirGjeldsreduksjon> =
                this.map { it.toGrunnlag() }
        }
    }

    data class OppjusteringAvEierinntekter(
        override val navn: String,
        override val beløp: String,
    ) : GrunnlagDbJson {
        override val spesifisering: List<SpesifiseringDbJson> = emptyList()
        override fun toGrunnlag(): Skattegrunnlag.Grunnlag.OppjusteringAvEierinntekter =
            Skattegrunnlag.Grunnlag.OppjusteringAvEierinntekter(
                navn = navn,
                beløp = beløp,
            )

        companion object {
            fun List<OppjusteringAvEierinntekter>.toDomain(): List<Skattegrunnlag.Grunnlag.OppjusteringAvEierinntekter> =
                this.map { it.toGrunnlag() }
        }
    }

    data class ManglerKategori(
        override val navn: String,
        override val beløp: String,
    ) : GrunnlagDbJson {
        override val spesifisering: List<SpesifiseringDbJson> = emptyList()
        override fun toGrunnlag(): Skattegrunnlag.Grunnlag.ManglerKategori = Skattegrunnlag.Grunnlag.ManglerKategori(
            navn = navn,
            beløp = beløp,
        )

        companion object {
            fun List<ManglerKategori>.toDomain(): List<Skattegrunnlag.Grunnlag.ManglerKategori> =
                this.map { it.toGrunnlag() }
        }
    }

    data class Annet(
        override val navn: String,
        override val beløp: String,
    ) : GrunnlagDbJson {
        override val spesifisering: List<SpesifiseringDbJson> = emptyList()
        override fun toGrunnlag(): Skattegrunnlag.Grunnlag.Annet = Skattegrunnlag.Grunnlag.Annet(
            navn = navn,
            beløp = beløp,
        )

        companion object {
            fun List<Annet>.toDomain(): List<Skattegrunnlag.Grunnlag.Annet> = this.map { it.toGrunnlag() }
        }
    }
}

sealed interface SpesifiseringDbJson {
    data class KjøretøyJson(
        val beløp: String? = null,
        val registreringsnummer: String? = null,
        val fabrikatnavn: String? = null,
        val årForFørstegangsregistrering: String? = null,
        val formuesverdi: String? = null,
        val antattVerdiSomNytt: String? = null,
        val antattMarkedsverdi: String? = null,
    ) : SpesifiseringDbJson {
        companion object {
            fun List<Skattegrunnlag.Spesifisering.Kjøretøy>.toDbJson(): List<KjøretøyJson> = this.map {
                KjøretøyJson(
                    beløp = it.beløp,
                    registreringsnummer = it.registreringsnummer,
                    fabrikatnavn = it.fabrikatnavn,
                    årForFørstegangsregistrering = it.årForFørstegangsregistrering,
                    formuesverdi = it.formuesverdi,
                    antattVerdiSomNytt = it.antattVerdiSomNytt,
                    antattMarkedsverdi = it.antattMarkedsverdi,
                )
            }

            fun List<KjøretøyJson>.toSpesifiseringKjøretøy(): List<Skattegrunnlag.Spesifisering.Kjøretøy> = this.map {
                Skattegrunnlag.Spesifisering.Kjøretøy(
                    beløp = it.beløp,
                    registreringsnummer = it.registreringsnummer,
                    fabrikatnavn = it.fabrikatnavn,
                    årForFørstegangsregistrering = it.årForFørstegangsregistrering,
                    formuesverdi = it.formuesverdi,
                    antattVerdiSomNytt = it.antattVerdiSomNytt,
                    antattMarkedsverdi = it.antattMarkedsverdi,
                )
            }
        }
    }
}
