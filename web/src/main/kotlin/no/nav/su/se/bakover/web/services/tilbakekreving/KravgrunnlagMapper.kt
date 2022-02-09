package no.nav.su.se.bakover.web.services.tilbakekreving

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
import java.time.LocalDate

internal object KravgrunnlagMapper {

    private val xmlMapper = XmlMapper(JacksonXmlModule().apply { setDefaultUseWrapper(false) }).apply {
        registerModule(KotlinModule.Builder().build())
        registerModule(JavaTimeModule())
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    fun mapTilKravmeldingRootDto(xml: String): KravmeldingRootDto {
        return xmlMapper.readValue(xml)
    }

    fun mapTilKravgrunnlag(råttKravgrunnlag: RåttKravgrunnlag): Kravgrunnlag {
        return mapTilKravmeldingRootDto(råttKravgrunnlag.melding).let { kravgrunnlag ->
            Kravgrunnlag(
                saksnummer = Saksnummer(kravgrunnlag.kravmeldingDto.fagsystemId.toLong()),
                vedtakId = kravgrunnlag.kravmeldingDto.vedtakId,
                kontrollfelt = kravgrunnlag.kravmeldingDto.kontrollfelt,
                behandler = NavIdentBruker.Saksbehandler(kravgrunnlag.kravmeldingDto.saksbehId),
                grunnlagsperioder = kravgrunnlag.kravmeldingDto.tilbakekrevingsperioder.map { tilbakekrevingsperiode ->
                    Kravgrunnlag.Grunnlagsperiode(
                        periode = Periode.create(
                            LocalDate.parse(tilbakekrevingsperiode.periode.fraOgMed),
                            LocalDate.parse(tilbakekrevingsperiode.periode.tilOgMed),
                        ),
                        grunnlagsbeløp = tilbakekrevingsperiode.tilbakekrevingsbeløp.map {
                            Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp(
                                kode = KlasseKode.valueOf(it.kodeKlasse),
                                type = KlasseType.valueOf(it.typeKlasse),
                            )
                        },
                    )
                },
            )
        }
    }
}
