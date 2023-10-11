package tilbakekreving.presentation.consumer

import arrow.core.Either
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.contains
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import økonomi.domain.KlasseKode
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Brukes for å mappe oppdrag sitt XML-format til en Kotlin-DTO og til domenemodellen vår.
 */
data object TilbakekrevingsmeldingMapper {

    private val xmlMapper = XmlMapper(JacksonXmlModule().apply { setDefaultUseWrapper(false) }).apply {
        registerModule(KotlinModule.Builder().build())
        registerModule(JavaTimeModule())
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    fun toDto(xml: String): Either<Throwable, TilbakekrevingsmeldingDto> {
        return Either.catch {
            xmlMapper.readTree(xml).let {
                if (it.contains("detaljertKravgrunnlag")) {
                    xmlMapper.readValue<KravgrunnlagRootDto>(xml)
                } else if (it.contains("kravOgVedtakstatus")) {
                    xmlMapper.readValue<KravgrunnlagStatusendringRootDto>(xml)
                } else {
                    throw IllegalArgumentException("Ukjent meldingstype, se kø for melding.")
                }
            }
        }
    }

    fun toXml(kravgrunnlagRootDto: KravgrunnlagRootDto): Either<Throwable, String> {
        return Either.catch {
            xmlMapper.writeValueAsString(kravgrunnlagRootDto)
        }
    }

    fun toKravgrunnlag(råttKravgrunnlag: RåttKravgrunnlag): Either<Throwable, Kravgrunnlag> {
        return toDto(råttKravgrunnlag.melding)
            .mapLeft { it }
            .map { tilbakekrevingsmeldingDto ->
                when (tilbakekrevingsmeldingDto) {
                    is KravgrunnlagRootDto -> {
                        tilbakekrevingsmeldingDto.kravgrunnlagDto.let { kravgrunnlagDto ->
                            Kravgrunnlag(
                                saksnummer = Saksnummer(kravgrunnlagDto.fagsystemId.toLong()),
                                eksternKravgrunnlagId = kravgrunnlagDto.kravgrunnlagId,
                                eksternVedtakId = kravgrunnlagDto.vedtakId,
                                status = when (kravgrunnlagDto.kodeStatusKrav) {
                                    "ANNU" -> Kravgrunnlag.KravgrunnlagStatus.Annulert
                                    "ANOM" -> Kravgrunnlag.KravgrunnlagStatus.AnnulertVedOmg
                                    "AVSL" -> Kravgrunnlag.KravgrunnlagStatus.Avsluttet
                                    "BEHA" -> Kravgrunnlag.KravgrunnlagStatus.Ferdigbehandlet
                                    "ENDR" -> Kravgrunnlag.KravgrunnlagStatus.Endret
                                    "FEIL" -> Kravgrunnlag.KravgrunnlagStatus.Feil
                                    "MANU" -> Kravgrunnlag.KravgrunnlagStatus.Manuell
                                    "NY" -> Kravgrunnlag.KravgrunnlagStatus.Nytt
                                    "SPER" -> Kravgrunnlag.KravgrunnlagStatus.Sperret
                                    else -> throw IllegalArgumentException("Ukjent kravgrunnlagstatus: ${kravgrunnlagDto.kodeStatusKrav}")
                                },
                                eksternKontrollfelt = kravgrunnlagDto.kontrollfelt,
                                behandler = kravgrunnlagDto.saksbehId,
                                utbetalingId = UUID30.fromString(kravgrunnlagDto.utbetalingId),
                                grunnlagsmåneder = kravgrunnlagDto.tilbakekrevingsperioder.map { tilbakekrevingsperiode ->
                                    Kravgrunnlag.Grunnlagsmåned(
                                        måned = Måned.Companion.fra(
                                            LocalDate.parse(tilbakekrevingsperiode.periode.fraOgMed),
                                            LocalDate.parse(tilbakekrevingsperiode.periode.tilOgMed),
                                        ),
                                        betaltSkattForYtelsesgruppen = BigDecimal(tilbakekrevingsperiode.skattebeløpPerMåned),
                                        ytelse = tilbakekrevingsperiode.tilbakekrevingsbeløp.filter { it.typeKlasse == "YTEL" }
                                            .let {
                                                it.single() // TODO - sjekk om vi alltid får tilbake 1. kaster hvis det potensielt finnes flere
                                                Kravgrunnlag.Grunnlagsmåned.Ytelse(
                                                    klassekode = KlasseKode.valueOf(it.single().kodeKlasse),
                                                    beløpTidligereUtbetaling = BigDecimal(it.single().belopOpprUtbet).intValueExact(),
                                                    beløpNyUtbetaling = BigDecimal(it.single().belopNy).intValueExact(),
                                                    beløpSkalTilbakekreves = BigDecimal(it.single().belopTilbakekreves).intValueExact(),
                                                    beløpSkalIkkeTilbakekreves = BigDecimal(it.single().belopUinnkrevd).intValueExact(),
                                                    skatteProsent = BigDecimal(it.single().skattProsent),
                                                )
                                            },
                                        feilutbetaling = tilbakekrevingsperiode.tilbakekrevingsbeløp.filter { it.typeKlasse == "FEIL" }
                                            .let {
                                                it.single() // TODO - sjekk om vi alltid får tilbake 1. kaster hvis det potensielt finnes flere
                                                Kravgrunnlag.Grunnlagsmåned.Feilutbetaling(
                                                    klassekode = KlasseKode.valueOf(it.single().kodeKlasse),
                                                    beløpTidligereUtbetaling = BigDecimal(it.single().belopOpprUtbet).intValueExact(),
                                                    beløpNyUtbetaling = BigDecimal(it.single().belopNy).intValueExact(),
                                                    beløpSkalTilbakekreves = BigDecimal(it.single().belopTilbakekreves).intValueExact(),
                                                    beløpSkalIkkeTilbakekreves = BigDecimal(it.single().belopUinnkrevd).intValueExact(),
                                                )
                                            },
                                    )
                                },
                                eksternTidspunkt = Tidspunkt(
                                    kontrollfeltFormatter.parse(
                                        kravgrunnlagDto.kontrollfelt,
                                        Instant::from,
                                    ),
                                ),
                            )
                        }
                    }

                    is KravgrunnlagStatusendringRootDto -> {
                        throw IllegalArgumentException("RåttKravgrunnlag innholder melding av type:${KravgrunnlagStatusendringRootDto::class}")
                    }
                }
            }
    }
}
