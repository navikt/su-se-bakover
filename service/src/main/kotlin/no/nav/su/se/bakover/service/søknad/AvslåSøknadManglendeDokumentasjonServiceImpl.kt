package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import arrow.core.getOrElse
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksattAvslåttSøknadsbehandlingResponse
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon.AvslåManglendeDokumentasjonCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon.KunneIkkeAvslåSøknad
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon.avslåSøknadPgaManglendeDokumentasjon
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import satser.domain.SatsFactory
import vilkår.formue.domain.FormuegrenserFactory
import økonomi.application.utbetaling.UtbetalingService
import java.time.Clock

class AvslåSøknadManglendeDokumentasjonServiceImpl(
    private val clock: Clock,
    private val sakService: SakService,
    private val satsFactory: SatsFactory,
    private val formuegrenserFactory: FormuegrenserFactory,
    private val iverksettSøknadsbehandlingService: IverksettSøknadsbehandlingService,
    private val utbetalingService: UtbetalingService,
    private val brevService: BrevService,
    private val oppgaveService: OppgaveService,
) : AvslåSøknadManglendeDokumentasjonService {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun avslå(
        command: AvslåManglendeDokumentasjonCommand,
    ): Either<KunneIkkeAvslåSøknad, Sak> {
        return lagAvslag(command).map {
            iverksettSøknadsbehandlingService.iverksett(it)
            it.sak
        }
    }

    override fun genererBrevForhåndsvisning(command: AvslåManglendeDokumentasjonCommand): Either<KunneIkkeAvslåSøknad, Pair<Fnr, PdfA>> {
        return lagAvslag(command).map { it.sak.fnr to it.dokument.generertDokument }
    }

    private fun lagAvslag(command: AvslåManglendeDokumentasjonCommand): Either<KunneIkkeAvslåSøknad, IverksattAvslåttSøknadsbehandlingResponse> {
        return sakService.hentSakForSøknad(command.søknadId)
            .getOrElse { throw IllegalArgumentException("Fant ikke søknad ${command.søknadId}. Kan ikke avslå søknad pga. manglende dokumentasjon.") }
            .let { sak ->
                sak.avslåSøknadPgaManglendeDokumentasjon(
                    command = command,
                    clock = clock,
                    satsFactory = satsFactory,
                    formuegrenserFactory = formuegrenserFactory,
                    genererPdf = brevService::lagDokument,
                    simulerUtbetaling = utbetalingService::simulerUtbetaling,
                    lukkOppgave = {
                        oppgaveService.lukkOppgave(it).mapLeft {
                            log.error("Kunne ikke lukke oppgave ved avslå pga manglende dokumentasjon for søknad ${command.søknadId}, for sak ${sak.id}. Feil var $it")
                            it
                        }
                    },
                )
            }
    }
}
