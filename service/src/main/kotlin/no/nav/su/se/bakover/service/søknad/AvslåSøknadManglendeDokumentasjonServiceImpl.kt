package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import arrow.core.getOrElse
import behandling.søknadsbehandling.domain.avslag.Avslag
import dokument.domain.KunneIkkeLageDokument
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.command.IverksettSøknadsbehandlingDokumentCommand
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
import vilkår.common.domain.Avslagsgrunn
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

    override fun genererBrevForhåndsvisning(command: AvslåManglendeDokumentasjonCommand): Either<KunneIkkeLageDokument, Pair<Fnr, PdfA>> {
        val sak = sakService.hentSakForSøknad(command.søknadId).getOrElse {
            throw IllegalArgumentException("Fant ikke søknad ${command.søknadId}. Kan ikke avslå søknad pga. manglende dokumentasjon.")
        }
        val søknad = sak.søknader.single { it.id == command.søknadId }
        // TODO jah: Her gjenbruker vi ikke logikken for forhåndsvisning og faktisk brev. Det føles tungvindt å gå veien om Sak.avslåSøknadPgaManglendeDokumentasjon(...) for å generere brev.
        //  På sikt bør vi flytte avslag pga. manglende dokumentasjon i den vanlige søknadsbehandlingsflyten.
        val dok = IverksettSøknadsbehandlingDokumentCommand.Avslag(
            fødselsnummer = sak.fnr,
            saksnummer = sak.saksnummer,
            avslag = Avslag(
                avslagsgrunner = listOf(Avslagsgrunn.MANGLENDE_DOKUMENTASJON),
                harEktefelle = false,
                beregning = null,
                formuegrunnlag = null,
                halvtGrunnbeløpPerÅr = satsFactory.grunnbeløp(søknad.opprettet.toLocalDate(zoneIdOslo))
                    .halvtGrunnbeløpPerÅrAvrundet(),
            ),
            saksbehandler = command.saksbehandler,
            attestant = null,
            fritekst = command.fritekstTilBrev,
            forventetInntektStørreEnn0 = false,
            satsoversikt = null,
            sakstype = sak.type,
        )
        return brevService.lagDokument(dok).map { Pair(sak.fnr, it.generertDokument) }
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
