package no.nav.su.se.bakover.web.services.tilbakekreving

import arrow.core.Either
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.contains
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Kravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttKravgrunnlag
import no.nav.su.se.bakover.domain.sak.Saksnummer
import java.math.BigDecimal
import java.time.LocalDate

object TilbakekrevingsmeldingMapper {

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

    fun toKravgrunnlg(råttKravgrunnlag: RåttKravgrunnlag): Either<Throwable, Kravgrunnlag> {
        return toDto(råttKravgrunnlag.melding)
            .mapLeft { it }
            .map { tilbakekrevingsmeldingDto ->
                when (tilbakekrevingsmeldingDto) {
                    is KravgrunnlagRootDto -> {
                        tilbakekrevingsmeldingDto.kravgrunnlagDto.let { kravgrunnlagDto ->
                            Kravgrunnlag(
                                saksnummer = Saksnummer(kravgrunnlagDto.fagsystemId.toLong()),
                                kravgrunnlagId = kravgrunnlagDto.kravgrunnlagId,
                                vedtakId = kravgrunnlagDto.vedtakId,
                                status = Kravgrunnlag.KravgrunnlagStatus.valueOf(kravgrunnlagDto.kodeStatusKrav),
                                kontrollfelt = kravgrunnlagDto.kontrollfelt,
                                behandler = NavIdentBruker.Saksbehandler(kravgrunnlagDto.saksbehId),
                                utbetalingId = UUID30.fromString(kravgrunnlagDto.utbetalingId),
                                grunnlagsperioder = kravgrunnlagDto.tilbakekrevingsperioder.map { tilbakekrevingsperiode ->
                                    Kravgrunnlag.Grunnlagsperiode(
                                        periode = Periode.create(
                                            LocalDate.parse(tilbakekrevingsperiode.periode.fraOgMed),
                                            LocalDate.parse(tilbakekrevingsperiode.periode.tilOgMed),
                                        ),
                                        beløpSkattMnd = BigDecimal(tilbakekrevingsperiode.skattebeløpPerMåned),
                                        grunnlagsbeløp = tilbakekrevingsperiode.tilbakekrevingsbeløp.map {
                                            Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp(
                                                kode = KlasseKode.valueOf(it.kodeKlasse),
                                                type = KlasseType.valueOf(it.typeKlasse),
                                                beløpTidligereUtbetaling = BigDecimal(it.belopOpprUtbet),
                                                beløpNyUtbetaling = BigDecimal(it.belopNy),
                                                beløpSkalTilbakekreves = BigDecimal(it.belopTilbakekreves),
                                                beløpSkalIkkeTilbakekreves = BigDecimal(it.belopUinnkrevd),
                                                skatteProsent = BigDecimal(it.skattProsent),
                                            )
                                        },
                                    )
                                },
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
