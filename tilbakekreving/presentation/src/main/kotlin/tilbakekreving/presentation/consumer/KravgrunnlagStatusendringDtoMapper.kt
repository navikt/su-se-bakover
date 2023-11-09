package tilbakekreving.presentation.consumer

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagStatusendringPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlagHendelse
import java.time.Clock
import java.time.Instant

private val log: Logger = LoggerFactory.getLogger("KravgrunnlagStatusendringDtoMapper.toHendelse")

internal fun KravgrunnlagStatusendringRootDto.toHendelse(
    hentSak: (Saksnummer) -> Either<Throwable, Sak>,
    clock: Clock,
    meta: DefaultHendelseMetadata,
    råttKravgrunnlagHendelse: RåttKravgrunnlagHendelse,
): Either<Throwable, Pair<Sak, KravgrunnlagStatusendringPåSakHendelse>> {
    val tidligereHendelseId = råttKravgrunnlagHendelse.hendelseId
    val hendelsestidspunkt = Tidspunkt.now(clock)
    return Either.catch {
        this.endringKravOgVedtakstatus.let {
            val saksnummer = Saksnummer.parse(it.fagsystemId)
            val sak = hentSak(saksnummer).getOrElse {
                return it.left()
            }
            sak to KravgrunnlagStatusendringPåSakHendelse(
                hendelseId = HendelseId.generer(),
                versjon = sak.versjon.inc(),
                sakId = sak.id,
                hendelsestidspunkt = hendelsestidspunkt,
                meta = meta,
                tidligereHendelseId = tidligereHendelseId,
                saksnummer = saksnummer,
                eksternVedtakId = it.vedtakId,
                status = it.kodeStatusKrav.toKravgrunnlagstatus(),
                eksternTidspunkt = råttKravgrunnlagHendelse.meta.jmsTimestamp?.let {
                    Tidspunkt(Instant.ofEpochSecond(it))
                } ?: hendelsestidspunkt.also {
                    log.error("Kunne ikke finne jmsTimestamp for kravgrunnlag, bruker hendelsestidspunkt istedet. Dersom hendelsene har kommet inn i feil rekkefølge, vil dette kunne påvirke sorteringen. RåttKravgrunnlagHendelse $tidligereHendelseId og sak ${sak.id}")
                },
            )
        }
    }
}
