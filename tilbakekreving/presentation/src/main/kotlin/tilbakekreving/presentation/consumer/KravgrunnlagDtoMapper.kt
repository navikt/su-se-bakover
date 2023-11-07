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
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Brukes for å mappe oppdrag sitt XML-format via [KravgrunnlagDto] til domenemodellen vår.
 */
data object KravgrunnlagDtoMapper {

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
                                    require(tilbakekrevingsperiode.tilbakekrevingsbeløp.size == 2) {
                                        "Forventer at det er to tilbakekrevingsbeløp per måned, en for ytelse og en for feilutbetaling. Hvis dette oppstår må man forstå det rå kravgrunnlaget på nytt."
                                    }
                                    Kravgrunnlag.Grunnlagsmåned(
                                        måned = Måned.Companion.fra(
                                            LocalDate.parse(tilbakekrevingsperiode.periode.fraOgMed),
                                            LocalDate.parse(tilbakekrevingsperiode.periode.tilOgMed),
                                        ),
                                        betaltSkattForYtelsesgruppen = BigDecimal(tilbakekrevingsperiode.skattebeløpPerMåned),
                                        ytelse = tilbakekrevingsperiode.tilbakekrevingsbeløp.filter { it.typeKlasse == "YTEL" && it.kodeKlasse == "SUUFORE" }
                                            .let {
                                                // Vi forventer kun en ytelse per måned
                                                val tilbakekrevingsbeløp = it.single()
                                                Kravgrunnlag.Grunnlagsmåned.Ytelse(
                                                    beløpTidligereUtbetaling = BigDecimal(tilbakekrevingsbeløp.belopOpprUtbet).intValueExact(),
                                                    beløpNyUtbetaling = BigDecimal(tilbakekrevingsbeløp.belopNy).intValueExact(),
                                                    beløpSkalTilbakekreves = BigDecimal(tilbakekrevingsbeløp.belopTilbakekreves).intValueExact(),
                                                    beløpSkalIkkeTilbakekreves = BigDecimal(tilbakekrevingsbeløp.belopUinnkrevd).intValueExact(),
                                                    skatteProsent = BigDecimal(tilbakekrevingsbeløp.skattProsent),
                                                )
                                            },
                                        feilutbetaling = tilbakekrevingsperiode.tilbakekrevingsbeløp.filter { it.typeKlasse == "FEIL" && it.kodeKlasse == "KL_KODE_FEIL_INNT" }
                                            .let {
                                                // Vi forventer kun en feilutbetaling per måned
                                                val tilbakekrevingsbeløp = it.single()
                                                require(BigDecimal(tilbakekrevingsbeløp.skattProsent).compareTo(BigDecimal.ZERO) == 0) {
                                                    "Forventer at skatteprosenten alltid er 0 for FEIL i kravgrunnlag fra oppdrag, men var ${tilbakekrevingsbeløp.skattProsent}"
                                                }
                                                Kravgrunnlag.Grunnlagsmåned.Feilutbetaling(
                                                    beløpTidligereUtbetaling = BigDecimal(tilbakekrevingsbeløp.belopOpprUtbet).intValueExact(),
                                                    beløpNyUtbetaling = BigDecimal(tilbakekrevingsbeløp.belopNy).intValueExact(),
                                                    beløpSkalTilbakekreves = BigDecimal(tilbakekrevingsbeløp.belopTilbakekreves).intValueExact(),
                                                    beløpSkalIkkeTilbakekreves = BigDecimal(tilbakekrevingsbeløp.belopUinnkrevd).intValueExact(),
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
