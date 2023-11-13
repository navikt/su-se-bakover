package tilbakekreving.application.service.consumer

import arrow.core.Either
import arrow.core.getOrElse
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelseskonsument
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagDetaljerPåSakHendelse
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagStatusendringPåSakHendelse
import tilbakekreving.infrastructure.repo.kravgrunnlag.MapRåttKravgrunnlagTilHendelse
import java.time.Clock

class KnyttKravgrunnlagTilSakOgUtbetalingKonsument(
    private val kravgrunnlagRepo: KravgrunnlagRepo,
    private val tilbakekrevingService: TilbakekrevingService,
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
    ): Either<Throwable, Unit> {
        return Either.catch {
            kravgrunnlagRepo.hentUprosesserteRåttKravgrunnlagHendelser(
                konsumentId = konsumentId,
            ).forEach { hendelseId ->
                val råttKravgrunnlagHendelse =
                    kravgrunnlagRepo.hentRåttKravgrunnlagHendelseForHendelseId(hendelseId) ?: run {
                        log.error("Kunne ikke prosessere kravgrunnlag: hentUprosesserteRåttKravgrunnlagHendelser returnerte hendelseId $hendelseId fra basen, men hentRåttKravgrunnlagHendelseForHendelseId fant den ikke. Denne vil prøves på nytt.")
                        return@forEach
                    }
                val (sak, kravgrunnlagPåSakHendelse) =
                    mapRåttKravgrunnlag(
                        råttKravgrunnlagHendelse,
                        correlationId,
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
                        return@forEach
                    }
                when (kravgrunnlagPåSakHendelse) {
                    is KravgrunnlagDetaljerPåSakHendelse -> prosesserDetaljer(
                        sak = sak,
                        hendelseId = hendelseId,
                        kravgrunnlagPåSakHendelse = kravgrunnlagPåSakHendelse,
                    )

                    is KravgrunnlagStatusendringPåSakHendelse -> prosesserStatus(
                        hendelseId = hendelseId,
                        kravgrunnlagPåSakHendelse = kravgrunnlagPåSakHendelse,
                    )
                }
            }
        }.onLeft {
            log.error("Feil under kjøring av hendelseskonsument $konsumentId", it)
        }
    }

    private fun prosesserStatus(
        hendelseId: HendelseId,
        kravgrunnlagPåSakHendelse: KravgrunnlagStatusendringPåSakHendelse,
    ) {
        // Statusendringene har ikke noen unik indikator i seg selv, annet enn JMS-meldingen sin id. Siden vi ikke får til noen god dedup. så vi aksepterer alle statusendringer.
        sessionFactory.withTransactionContext { tx ->
            kravgrunnlagRepo.lagreKravgrunnlagPåSakHendelse(kravgrunnlagPåSakHendelse, tx)
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
    ) {
        val kravgrunnlag = kravgrunnlagPåSakHendelse.kravgrunnlag
        val eksternKravgrunnlagId = kravgrunnlag.eksternKravgrunnlagId
        val sakId = sak.id
        val saksnummer = sak.saksnummer

        if (kravgrunnlagRepo.hentKravgrunnlagPåSakHendelser(sakId).detaljerSortert.any {
                it.kravgrunnlag.eksternKravgrunnlagId == eksternKravgrunnlagId
            }
        ) {
            log.error("Kunne ikke prosessere kravgrunnlag: Fant eksisterende kravgrunnlag knyttet til sak med eksternKravgrunnlagId $eksternKravgrunnlagId på sak $saksnummer og hendelse $hendelseId. Ignorerer hendelsen.")
            hendelsekonsumenterRepo.lagre(
                hendelseId = hendelseId,
                konsumentId = konsumentId,
            )
            return
        }
        sessionFactory.withTransactionContext { tx ->
            kravgrunnlagRepo.lagreKravgrunnlagPåSakHendelse(kravgrunnlagPåSakHendelse, tx)
            hendelsekonsumenterRepo.lagre(
                hendelseId = hendelseId,
                konsumentId = konsumentId,
                context = tx,
            )
            val revurderingId = kravgrunnlagPåSakHendelse.revurderingId
            if (revurderingId != null) {
                val revurdering = sak.hentRevurdering(revurderingId).getOrNull() as IverksattRevurdering

                (revurdering.tilbakekrevingsbehandling as? Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.AvventerKravgrunnlag)?.mottattKravgrunnlag(
                    kravgrunnlag = kravgrunnlag,
                    kravgrunnlagMottatt = Tidspunkt.now(clock),
                    hentRevurdering = { revurdering },
                )?.also { mottattKravgrunnlagForRevurdering ->
                    // I en overgangsfase erstatter denne det den gamle kravgrunnlagkonsumeren gjorde. Disse plukkes opp av jobben [no.nav.su.se.bakover.web.services.tilbakekreving.SendTilbakekrevingsvedtakForRevurdering]
                    tilbakekrevingService.lagre(mottattKravgrunnlagForRevurdering)
                    log.info("Oppdaterte revurderingen sin tilbakekreving ${mottattKravgrunnlagForRevurdering.avgjort.id} til mottatt kravgrunnlag for revurdering ${mottattKravgrunnlagForRevurdering.avgjort.revurderingId}")
                }
                    ?: log.error("Knyttet kravgrunnlag til sak for revurdering $revurderingId, sak $sakId. Forventet: avventet kravgrunnlag, men typen var: ${revurdering.tilbakekrevingsbehandling::class}")
            }
        }
    }
}
