package no.nav.su.se.bakover.web.services.tilbakekreving

import arrow.core.Either
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Kravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttKravgrunnlag
import java.math.BigDecimal
import java.time.LocalDate

internal object KravgrunnlagMapper {

    private val xmlMapper = XmlMapper(JacksonXmlModule().apply { setDefaultUseWrapper(false) }).apply {
        registerModule(KotlinModule.Builder().build())
        registerModule(JavaTimeModule())
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    fun toDto(xml: String): Either<Throwable, KravmeldingDto> {
        return Either.catch {
            xmlMapper.readValue<KravmeldingRootDto>(xml).kravmeldingDto
        }
    }

    fun toXml(kravmeldingRootDto: KravmeldingRootDto): Either<Throwable, String> {
        return Either.catch {
            xmlMapper.writeValueAsString(kravmeldingRootDto)
        }
    }

    fun toKravgrunnlg(råttKravgrunnlag: RåttKravgrunnlag): Either<Throwable, Kravgrunnlag> {
        return toDto(råttKravgrunnlag.melding)
            .mapLeft { it }
            .map { kravgrunnlag ->
                Kravgrunnlag(
                    saksnummer = Saksnummer(kravgrunnlag.fagsystemId.toLong()),
                    kravgrunnlagId = kravgrunnlag.kravgrunnlagId,
                    vedtakId = kravgrunnlag.vedtakId,
                    status = Kravgrunnlag.KravgrunnlagStatus.valueOf(kravgrunnlag.kodeStatusKrav),
                    kontrollfelt = kravgrunnlag.kontrollfelt,
                    behandler = NavIdentBruker.Saksbehandler(kravgrunnlag.saksbehId),
                    grunnlagsperioder = kravgrunnlag.tilbakekrevingsperioder.map { tilbakekrevingsperiode ->
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
}
