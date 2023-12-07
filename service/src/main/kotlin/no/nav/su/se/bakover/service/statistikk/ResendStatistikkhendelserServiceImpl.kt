package no.nav.su.se.bakover.service.statistikk

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.Klagevedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRegulering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakIverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørMedUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørUtenUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import no.nav.su.se.bakover.vedtak.application.VedtakService
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Har som ansvar og resende hendelser som ikke har blitt sendt til statistikk.
 */
class ResendStatistikkhendelserServiceImpl(
    private val vedtakService: VedtakService,
    private val sakRepo: SakRepo,
    private val statistikkEventObserver: StatistikkEventObserver,
) : ResendStatistikkhendelserService {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun resendIverksattSøknadsbehandling(
        fraOgMedDato: LocalDate,
    ) {
        log.info("Resend statistikk: Starter resend av statistikk for søknadsbehandlingvedtak fra og med $fraOgMedDato.")
        vedtakService.hentSøknadsbehandlingsvedtakFraOgMed(fraOgMedDato).also {
            log.info("Resend statistikk: Fant ${it.size} søknadsbehandlingvedtak fra og med $fraOgMedDato som skal resendes.")
        }.forEachIndexed { index, vedtakId ->
            resendStatistikkForVedtak(vedtakId, VedtakIverksattSøknadsbehandling::class).onLeft {
                if (index == 0) {
                    log.error("Resend statistikk: Siden vi feilet på første element, avbryter vi. FraOgMedDato: $fraOgMedDato, vedtakId:$vedtakId")
                    return
                }
            }
        }
    }

    override fun resendStatistikkForVedtak(
        vedtakId: UUID,
        requiredType: KClass<*>?,
    ): Either<Unit, Unit> {
        log.info("Resend statistikk: $vedtakId")
        sakRepo.hentSakForVedtak(vedtakId).let { sak ->
            val vedtak = Either.catch {
                sak!!.vedtakListe.single { it.id == vedtakId }.also {
                    if (requiredType != null) {
                        require(it::class == requiredType)
                    }
                }
            }.getOrElse {
                log.error(
                    "Resend statistikk: Fant ikke sak for vedtak ($vedtakId) eller var ikke av type $requiredType.",
                    it,
                )
                return Unit.left()
            }
            when (vedtak) {
                is VedtakInnvilgetSøknadsbehandling -> {
                    statistikkEventObserver.handle(StatistikkEvent.Behandling.Søknad.Iverksatt.Innvilget(vedtak))
                    statistikkEventObserver.handle(StatistikkEvent.Stønadsvedtak(vedtak) { sak!! })
                    log.info("Resend statistikk: Sendte statistikk for VedtakInnvilgetSøknadsbehandling $vedtakId.")
                }

                is Avslagsvedtak -> {
                    statistikkEventObserver.handle(StatistikkEvent.Behandling.Søknad.Iverksatt.Avslag(vedtak))
                    log.info("Resend statistikk: Sendte statistikk for Avslagsvedtak $vedtakId.")
                }

                is Klagevedtak.Avvist -> {
                    statistikkEventObserver.handle(StatistikkEvent.Behandling.Klage.Avvist(vedtak))
                    log.info("Resend statistikk: Sendte statistikk for Klagevedtak.Avvist $vedtakId.")
                }

                is VedtakOpphørUtenUtbetaling -> {
                    statistikkEventObserver.handle(StatistikkEvent.Behandling.Revurdering.Iverksatt.Opphørt(vedtak))
                    statistikkEventObserver.handle(StatistikkEvent.Stønadsvedtak(vedtak) { sak!! })
                    log.error("Resend statistikk: Sendte statistikk for VedtakOpphørAvkorting $vedtakId. Dette skal ikke skje, siden vi ikke lager nye vedtak av denne typen, men vi øsnker ikke feile hvis det oppstår.")
                }

                is VedtakOpphørMedUtbetaling -> {
                    statistikkEventObserver.handle(StatistikkEvent.Behandling.Revurdering.Iverksatt.Opphørt(vedtak))
                    statistikkEventObserver.handle(StatistikkEvent.Stønadsvedtak(vedtak) { sak!! })
                    log.info("Resend statistikk: Sendte statistikk for VedtakOpphørMedUtbetaling $vedtakId.")
                }

                is VedtakInnvilgetRevurdering -> {
                    statistikkEventObserver.handle(StatistikkEvent.Behandling.Revurdering.Iverksatt.Innvilget(vedtak))
                    statistikkEventObserver.handle(StatistikkEvent.Stønadsvedtak(vedtak) { sak!! })
                    log.info("Resend statistikk: Sendte statistikk for VedtakInnvilgetRevurdering $vedtakId.")
                }

                is VedtakGjenopptakAvYtelse -> {
                    statistikkEventObserver.handle(StatistikkEvent.Behandling.Gjenoppta.Iverksatt(vedtak))
                    statistikkEventObserver.handle(StatistikkEvent.Stønadsvedtak(vedtak) { sak!! })
                    log.info("Resend statistikk: Sendte statistikk for VedtakGjenopptakAvYtelse $vedtakId.")
                }

                is VedtakInnvilgetRegulering -> {
                    throw IllegalArgumentException("Kan ikke sende statistikk for reguleringsvedtak")
                }

                is VedtakStansAvYtelse -> {
                    statistikkEventObserver.handle(StatistikkEvent.Behandling.Stans.Iverksatt(vedtak))
                    statistikkEventObserver.handle(StatistikkEvent.Stønadsvedtak(vedtak) { sak!! })
                    log.info("Resend statistikk: Sendte statistikk for VedtakStansAvYtelse $vedtakId.")
                }
            }
            return Unit.right()
        }
    }
}
