package tilbakekreving.application.service

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.toNonEmptyListOrNone
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.tilMåned
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelseskonsument
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tilbakekreving.domain.kravgrunnlag.Grunnlagsmåned
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlag
import tilbakekreving.domain.kravgrunnlag.RåttKravgrunnlagHendelse
import java.time.Clock

class KnyttKravgrunnlagTilSakOgUtbetalingKonsument(
    private val kravgrunnlagRepo: KravgrunnlagRepo,
    private val sakService: SakService,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val mapRåttKravgrunnlag: (RåttKravgrunnlag) -> Kravgrunnlag,
    private val clock: Clock,
    private val sessionFactory: SessionFactory,
) : Hendelseskonsument {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override val konsumentId = HendelseskonsumentId("KnyttKravgrunnlagTilSakOgUtbetaling")

    fun knyttKravgrunnlagTilSakOgUtbetaling(
        correlationId: CorrelationId,
    ) {
        Either.catch {
            kravgrunnlagRepo.hentUprosesserteRåttKravgrunnlagHendelser(
                konsumentId = konsumentId,
            ).forEach { hendelseId ->
                val råttKravgrunnlagHendelse =
                    kravgrunnlagRepo.hentRåttKravgrunnlagHendelseForHendelseId(hendelseId) ?: run {
                        log.error("Kunne ikke prosessere kravgrunnlag: hentUprosesserteRåttKravgrunnlagHendelser returnerte hendelseId $hendelseId fra basen, men hentRåttKravgrunnlagHendelseForHendelseId fant den ikke. Denne vil prøves på nytt.")
                        return@forEach
                    }
                val kravgrunnlag = mapRåttKravgrunnlag(råttKravgrunnlagHendelse.råttKravgrunnlag)
                val saksnummer = kravgrunnlag.saksnummer
                val sak = sakService.hentSak(saksnummer).getOrElse {
                    log.error("Kunne ikke prosessere kravgrunnlag: Fant ikke sak med saksnummer $saksnummer, hendelse $hendelseId. Denne vil prøves på nytt.")
                    return@forEach
                }
                if (kravgrunnlagRepo.hentKravgrunnlagPåSakHendelser(sak.id).any {
                        it.eksternKravgrunnlagId == kravgrunnlag.kravgrunnlagId
                    }
                ) {
                    log.error("Kunne ikke prosessere kravgrunnlag: Fant eksisterende kravgrunnlag knyttet til sak med eksternKravgrunnlagId ${kravgrunnlag.kravgrunnlagId} på sak $saksnummer og hendelse $hendelseId. Ignorerer hendelsen.")
                    hendelsekonsumenterRepo.lagre(
                        hendelseId = råttKravgrunnlagHendelse.hendelseId,
                        konsumentId = konsumentId,
                    )
                    return@forEach
                }
                val utbetaling =
                    sak.utbetalinger.filter { it.id == kravgrunnlag.utbetalingId }.toNonEmptyListOrNone().getOrElse {
                        log.error("Kunne ikke prosessere kravgrunnlag: Fant ikke utbetaling med id ${kravgrunnlag.utbetalingId} på sak $saksnummer og hendelse $hendelseId. Kanskje den ikke er opprettet enda? Prøver igjen ved neste kjøring.")
                        return@forEach
                    }.single()
                when (utbetaling) {
                    is Utbetaling.SimulertUtbetaling,
                    is Utbetaling.UtbetalingForSimulering,
                    is Utbetaling.OversendtUtbetaling.UtenKvittering,
                    -> throw IllegalStateException("Kunne ikke prosessere kravgrunnlag: Utbetalingen skal ikke være i tilstanden ${utbetaling::class.simpleName} for utbetalingId ${utbetaling.id} og hendelse $hendelseId")

                    is Utbetaling.OversendtUtbetaling.MedKvittering -> {
                        val nyHendelse = nyHendelse(sak, correlationId, råttKravgrunnlagHendelse, kravgrunnlag)
                        sessionFactory.withTransactionContext { tx ->
                            kravgrunnlagRepo.lagreKravgrunnlagPåSakHendelse(nyHendelse, tx)
                            hendelsekonsumenterRepo.lagre(
                                hendelseId = råttKravgrunnlagHendelse.hendelseId,
                                konsumentId = konsumentId,
                                context = tx,
                            )
                        }
                    }
                }
            }
        }.onLeft {
            log.error("Feil under kjøring av hendelseskonsument $konsumentId", it)
        }
    }

    private fun nyHendelse(
        sak: Sak,
        correlationId: CorrelationId,
        råttKravgrunnlagHendelse: RåttKravgrunnlagHendelse,
        kravgrunnlag: Kravgrunnlag,
    ) = KravgrunnlagPåSakHendelse(
        hendelseId = HendelseId.generer(),
        versjon = sak.versjon.inc(),
        sakId = sak.id,
        hendelsestidspunkt = Tidspunkt.now(clock),
        meta = HendelseMetadata.fraCorrelationId(correlationId),
        tidligereHendelseId = råttKravgrunnlagHendelse.hendelseId,
        eksternKravgrunnlagId = kravgrunnlag.kravgrunnlagId,
        eksternVedtakId = kravgrunnlag.vedtakId,
        eksternKontrollfelt = kravgrunnlag.kontrollfelt,
        status = kravgrunnlag.status,
        behandler = kravgrunnlag.behandler.navIdent,
        utbetalingId = kravgrunnlag.utbetalingId,
        grunnlagsmåneder = kravgrunnlag.grunnlagsperioder.map {
            Grunnlagsmåned(
                måned = it.periode.tilMåned(),
                betaltSkattForYtelsesgruppen = it.beløpSkattMnd,
                grunnlagsbeløp = it.grunnlagsbeløp.toNonEmptyList(),
            )
        }.toNonEmptyList(),
    )
}
