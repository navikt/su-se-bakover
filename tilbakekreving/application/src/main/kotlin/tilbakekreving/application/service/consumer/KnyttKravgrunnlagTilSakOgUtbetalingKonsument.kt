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
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.TilbakekrevingsbehandlingUnderRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelseskonsument
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingUnderRevurderingService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagDetaljerPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagStatusendringPåSakHendelse
import tilbakekreving.infrastructure.repo.kravgrunnlag.MapRåttKravgrunnlagTilHendelse
import java.time.Clock

class KnyttKravgrunnlagTilSakOgUtbetalingKonsument(
    private val kravgrunnlagRepo: KravgrunnlagRepo,
    private val tilbakekrevingService: TilbakekrevingUnderRevurderingService,
    private val sakService: SakService,
    private val hendelsekonsumenterRepo: HendelsekonsumenterRepo,
    private val mapRåttKravgrunnlag: MapRåttKravgrunnlagTilHendelse,
    private val clock: Clock,
    private val sessionFactory: SessionFactory,
) : Hendelseskonsument {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override val konsumentId = HendelseskonsumentId("KnyttKravgrunnlagTilSakOgUtbetaling")

    /**
     * Funksjonen logger feilene selv, men returnerer en throwable for testene sin del.
     */
    fun knyttKravgrunnlagTilSakOgUtbetaling(
        correlationId: CorrelationId,
    ): Either<Nel<Throwable>, Unit> {
        return Either.catch {
            kravgrunnlagRepo.hentUprosesserteRåttKravgrunnlagHendelser(
                konsumentId = konsumentId,
            ).map { hendelseId ->
                prosesserEnHendelse(hendelseId, correlationId)
            }
        }.mapLeft {
            log.error(
                "Kunne ikke prosessere kravgrunnlag: Det ble kastet en exception ved hentUprosesserteRåttKravgrunnlagHendelser for konsument $konsumentId",
                it,
            )
            nonEmptyListOf(it)
        }.flatMap {
            it.flattenOrAccumulate().map { }
        }
    }

    private fun prosesserEnHendelse(
        hendelseId: HendelseId,
        correlationId: CorrelationId,
    ): Either<Throwable, Unit> {
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
                return Unit.right()
            }
            when (kravgrunnlagPåSakHendelse) {
                is KravgrunnlagDetaljerPåSakHendelse -> prosesserDetaljer(
                    sak = sak,
                    hendelseId = hendelseId,
                    kravgrunnlagPåSakHendelse = kravgrunnlagPåSakHendelse,
                    correlationId = correlationId,
                )

                is KravgrunnlagStatusendringPåSakHendelse -> prosesserStatus(
                    hendelseId = hendelseId,
                    kravgrunnlagPåSakHendelse = kravgrunnlagPåSakHendelse,
                    correlationId = correlationId,
                )
            }
        }.onLeft {
            log.error("Kunne ikke prosessere kravgrunnlag: Det ble kastet en exception for hendelsen $hendelseId", it)
        }
    }

    private fun prosesserStatus(
        hendelseId: HendelseId,
        kravgrunnlagPåSakHendelse: KravgrunnlagStatusendringPåSakHendelse,
        correlationId: CorrelationId,
    ) {
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
    }

    private fun prosesserDetaljer(
        sak: Sak,
        hendelseId: HendelseId,
        kravgrunnlagPåSakHendelse: KravgrunnlagDetaljerPåSakHendelse,
        correlationId: CorrelationId,
    ) {
        val kravgrunnlag = kravgrunnlagPåSakHendelse.kravgrunnlag
        val sakId = sak.id

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
            val revurderingId = kravgrunnlagPåSakHendelse.revurderingId
            if (revurderingId != null) {
                val revurdering = sak.hentRevurdering(revurderingId).getOrNull() as IverksattRevurdering

                (revurdering.tilbakekrevingsbehandling as? TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag)?.mottattKravgrunnlag(
                    kravgrunnlag = kravgrunnlag,
                    kravgrunnlagMottatt = Tidspunkt.now(clock),
                    hentRevurdering = { revurdering },
                )?.also { mottattKravgrunnlagForRevurdering ->
                    // I en overgangsfase erstatter denne det den gamle kravgrunnlagkonsumeren gjorde. Disse plukkes opp av jobben [no.nav.su.se.bakover.web.services.tilbakekreving.SendTilbakekrevingsvedtakForRevurdering]
                    tilbakekrevingService.lagre(mottattKravgrunnlagForRevurdering, tx)
                    log.info("Oppdaterte revurderingen sin tilbakekreving ${mottattKravgrunnlagForRevurdering.avgjort.id} til mottatt kravgrunnlag for revurdering ${mottattKravgrunnlagForRevurdering.avgjort.revurderingId}")
                }
                    ?: log.error("Knyttet kravgrunnlag til sak for revurdering $revurderingId, sak $sakId. Forventet: avventet kravgrunnlag, men typen var: ${revurdering.tilbakekrevingsbehandling::class}")
            }
        }
    }
}
