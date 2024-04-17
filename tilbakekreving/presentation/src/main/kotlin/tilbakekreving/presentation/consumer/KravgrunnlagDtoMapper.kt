package tilbakekreving.presentation.consumer

import arrow.core.Either
import arrow.core.flatMap
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.contains
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.JMSHendelseMetadata
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.rått.RåttKravgrunnlag
import tilbakekreving.domain.kravgrunnlag.rått.RåttKravgrunnlagHendelse
import java.time.Clock

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

    /**
     * Historisk.
     * Da vi hadde tilbakekreving under revurdering, lagret vi kun kravgrunnlagsXMLen vi mottok fra Oppdrag i databasetabellen 'revurdering_tilbakekreving'.
     * Denne brukes for å mappe det kravgrunnlaget til domenemodellen vår, slik at vi kan vise det i frontend.
     */
    fun toKravgrunnlag(råttKravgrunnlag: RåttKravgrunnlag): Either<Throwable, Kravgrunnlag> {
        return toDto(råttKravgrunnlag.melding)
            .mapLeft { it }
            .flatMap { tilbakekrevingsmeldingDto ->
                when (tilbakekrevingsmeldingDto) {
                    // Gamle tilbakekrevingsrutinen: Vi hadde ikke hendelser her og bruker heller ikke den i disse tilfellene.
                    is KravgrunnlagRootDto -> tilbakekrevingsmeldingDto.toDomain(HendelseId.generer())

                    is KravgrunnlagStatusendringRootDto -> {
                        throw IllegalArgumentException("RåttKravgrunnlag innholder melding av type:${KravgrunnlagStatusendringRootDto::class}")
                    }
                }
            }
    }

    fun toKravgrunnlagPåSakHendelse(
        råttKravgrunnlagHendelse: RåttKravgrunnlagHendelse,
        metaTilHendelsen: JMSHendelseMetadata,
        hentSak: (Saksnummer) -> Either<Throwable, Sak>,
        clock: Clock,
    ): Either<Throwable, Pair<Sak, KravgrunnlagPåSakHendelse>> {
        return toDto(råttKravgrunnlagHendelse.råttKravgrunnlag.melding)
            .mapLeft { it }
            .flatMap { tilbakekrevingsmeldingDto ->
                when (tilbakekrevingsmeldingDto) {
                    is KravgrunnlagRootDto -> tilbakekrevingsmeldingDto.toHendelse(
                        hentSak = hentSak,
                        hendelsesTidspunkt = Tidspunkt.now(clock),
                        tidligereHendelseId = råttKravgrunnlagHendelse.hendelseId,
                    )

                    is KravgrunnlagStatusendringRootDto -> {
                        tilbakekrevingsmeldingDto.toHendelse(
                            hentSak = hentSak,
                            clock = clock,
                            metaTilHendelsen = metaTilHendelsen,
                            råttKravgrunnlagHendelse = råttKravgrunnlagHendelse,
                        )
                    }
                }
            }
    }
}
