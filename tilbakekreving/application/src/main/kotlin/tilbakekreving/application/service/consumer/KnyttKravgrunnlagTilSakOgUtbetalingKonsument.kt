package tilbakekreving.application.service.consumer

import arrow.core.Either
import arrow.core.Nel
import arrow.core.flatMap
import arrow.core.flattenOrAccumulate
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelseskonsument
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagDetaljerPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.påsak.KravgrunnlagStatusendringPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.repo.KravgrunnlagRepo
import tilbakekreving.infrastructure.repo.kravgrunnlag.MapRåttKravgrunnlagTilHendelse
import java.time.Clock

class KnyttKravgrunnlagTilSakOgUtbetalingKonsument(
    private val kravgrunnlagRepo: KravgrunnlagRepo,
    private val sakService: SakService,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val mapRåttKravgrunnlag: MapRåttKravgrunnlagTilHendelse,
    private val clock: Clock,
    private val sessionFactory: SessionFactory,
) : Hendelseskonsument {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override val konsumentId = HendelseskonsumentId("KnyttKravgrunnlagTilSakOgUtbetaling")

    /**
     * Funksjonen logger feilene selv, men returnerer for testene sin del.
     */
    fun knyttKravgrunnlagTilSakOgUtbetaling(
        correlationId: CorrelationId,
    ): Either<Nel<Throwable>, List<HendelseId>> {
        return Either.catch {
            kravgrunnlagRepo.hentUprosesserteRåttKravgrunnlagHendelser(konsumentId = konsumentId)
                .map { hendelseId -> prosesserEnHendelse(hendelseId, correlationId) }
        }.mapLeft {
            log.error(
                "Kunne ikke prosessere kravgrunnlag: Det ble kastet en exception ved hentUprosesserteRåttKravgrunnlagHendelser for konsument $konsumentId",
                it,
            )
            nonEmptyListOf(it)
        }.flatMap {
            it.flattenOrAccumulate().map {
                it.mapNotNull { it.knyttetKravgrunnlagPåSakHendelse }
            }
        }
    }

    private fun prosesserEnHendelse(
        hendelseId: HendelseId,
        correlationId: CorrelationId,
    ): Either<Throwable, ProsseserteHendelser> {
        return Either.catch {
            val (råttKravgrunnlagHendelse, meta) =
                kravgrunnlagRepo.hentRåttKravgrunnlagHendelseMedMetadataForHendelseId(hendelseId) ?: run {
                    log.error("Kunne ikke prosessere kravgrunnlag: hentUprosesserteRåttKravgrunnlagHendelser returnerte hendelseId $hendelseId fra basen, men hentRåttKravgrunnlagHendelseForHendelseId fant den ikke. Denne vil prøves på nytt.")
                    return IllegalStateException("Kunne ikke prosessere kravgrunnlag. Se logger.").left()
                }
            val (sak, kravgrunnlagPåSakHendelse) =
                mapRåttKravgrunnlag(
                    råttKravgrunnlagHendelse,
                    meta,
                    { saksnummer ->
                        sakService.hentSak(saksnummer).mapLeft {
                            IllegalStateException("Kunne ikke prosessere kravgrunnlag: mapRåttKravgrunnlag feilet for sak $saksnummer hendelseId $hendelseId. Denne vil prøves på nytt.")
                        }
                    },
                    clock,
                ).getOrElse {
                    log.error(
                        "Kunne ikke prosessere kravgrunnlag: mapRåttKravgrunnlag feilet for hendelseId $hendelseId. Denne vil prøves på nytt.",
                        it,
                    )
                    return IllegalStateException("Kunne ikke prosessere kravgrunnlag. Se logger.").left()
                }
            if (kravgrunnlagRepo.hentKravgrunnlagPåSakHendelser(sak.id).any { it.tidligereHendelseId == hendelseId }) {
                log.info("Vi har allerede knyttet det rå kravgrunnlaget til en sak. Denne vil ikke prøves på nytt. Hendelse $hendelseId, sak ${sak.id}")
                hendelsekonsumenterRepo.lagre(
                    hendelseId = hendelseId,
                    konsumentId = konsumentId,
                )
                return ProsseserteHendelser(hendelseId, null).right()
            }
            when (kravgrunnlagPåSakHendelse) {
                is KravgrunnlagDetaljerPåSakHendelse -> {
                    ProsseserteHendelser(
                        hendelseId,
                        prosesserDetaljer(
                            hendelseId = hendelseId,
                            kravgrunnlagPåSakHendelse = kravgrunnlagPåSakHendelse,
                            correlationId = correlationId,
                        ),
                    )
                }

                is KravgrunnlagStatusendringPåSakHendelse -> {
                    ProsseserteHendelser(
                        hendelseId,
                        prosesserStatus(
                            hendelseId = hendelseId,
                            kravgrunnlagPåSakHendelse = kravgrunnlagPåSakHendelse,
                            correlationId = correlationId,
                        ),
                    )
                }
            }
        }.onLeft {
            log.error("Kunne ikke prosessere kravgrunnlag: Det ble kastet en exception for hendelsen $hendelseId", it)
        }
    }

    private fun prosesserStatus(
        hendelseId: HendelseId,
        kravgrunnlagPåSakHendelse: KravgrunnlagStatusendringPåSakHendelse,
        correlationId: CorrelationId,
    ): HendelseId {
        // Statusendringene har ikke noen unik indikator i seg selv, annet enn JMS-meldingen sin id. Siden vi ikke får til noen god dedup. så vi aksepterer alle statusendringer.
        sessionFactory.withTransactionContext { tx ->
            kravgrunnlagRepo.lagreKravgrunnlagPåSakHendelse(
                hendelse = kravgrunnlagPåSakHendelse,
                meta = DefaultHendelseMetadata.fraCorrelationId(correlationId),
                sessionContext = tx,
            )
            hendelsekonsumenterRepo.lagre(
                hendelseId = hendelseId,
                konsumentId = konsumentId,
                context = tx,
            )
        }
        return kravgrunnlagPåSakHendelse.hendelseId
    }

    private fun prosesserDetaljer(
        hendelseId: HendelseId,
        kravgrunnlagPåSakHendelse: KravgrunnlagDetaljerPåSakHendelse,
        correlationId: CorrelationId,
    ): HendelseId {
        sessionFactory.withTransactionContext { tx ->
            kravgrunnlagRepo.lagreKravgrunnlagPåSakHendelse(
                hendelse = kravgrunnlagPåSakHendelse,
                meta = DefaultHendelseMetadata.fraCorrelationId(correlationId),
                sessionContext = tx,
            )
            hendelsekonsumenterRepo.lagre(
                hendelseId = hendelseId,
                konsumentId = konsumentId,
                context = tx,
            )
        }
        return kravgrunnlagPåSakHendelse.hendelseId
    }
}

private data class ProsseserteHendelser(
    /**
     * aka tidligere hendelseId for nye hendelsen som er blitt knyttet til saken
     */
    val hendelsenSomErBlittProsessert: HendelseId,
    val knyttetKravgrunnlagPåSakHendelse: HendelseId?,
)
