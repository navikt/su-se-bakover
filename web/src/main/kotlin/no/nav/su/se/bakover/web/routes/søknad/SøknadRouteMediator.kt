package no.nav.su.se.bakover.web.routes.søknad

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.database.søknad.AvsluttetSøknadsBehandlingOK
import no.nav.su.se.bakover.database.søknad.KunneIkkeAvslutteSøknadsBehandling
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.AvsluttSøknadsBehandlingBody
import no.nav.su.se.bakover.domain.AvsluttSøkndsBehandlingBegrunnelse
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.web.services.brev.BrevService
import org.slf4j.LoggerFactory
import java.util.UUID

internal class SøknadRouteMediator(
    private val pdfGenerator: PdfGenerator,
    private val dokArkiv: DokArkiv,
    private val oppgaveClient: OppgaveClient,
    private val personOppslag: PersonOppslag,
    private val søknadService: SøknadService,
    private val sakService: SakService,
    private val brevService: BrevService
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun nySøknad(søknadInnhold: SøknadInnhold): Sak {
        // TODO shift this to service
        val sak = sakService.hentSak(søknadInnhold.personopplysninger.fnr)
            .fold(
                { sakService.opprettSak(søknadInnhold.personopplysninger.fnr) },
                { it }
            )
        val søknad = søknadService.opprettSøknad(sak.id, Søknad(søknadInnhold = søknadInnhold))
        opprettJournalpostOgOppgave(sak.id, søknad)
        return sakService.hentSak(søknadInnhold.personopplysninger.fnr)
            .getOrElse { throw RuntimeException("Kunne ikke hente sak") }
    }

    fun avsluttSøknadsBehandling(
        avsluttSøknadsBehandlingBody: AvsluttSøknadsBehandlingBody
    ): Either<KunneIkkeAvslutteSøknadsBehandling, AvsluttetSøknadsBehandlingOK> {
        val loggtema = "Avslutting av søknadsbehandling"

        if (avsluttSøknadsBehandlingBody.avsluttSøkndsBehandlingBegrunnelse
            == AvsluttSøkndsBehandlingBegrunnelse.Bortfalt ||
            avsluttSøknadsBehandlingBody.avsluttSøkndsBehandlingBegrunnelse
            == AvsluttSøkndsBehandlingBegrunnelse.AvvistSøktForTidlig
        ) {
            log.info("Bortfalt og avvistSøktForTidlig er ennå ikke implementert :)")
        }

        if (avsluttSøknadsBehandlingBody.avsluttSøkndsBehandlingBegrunnelse == AvsluttSøkndsBehandlingBegrunnelse.Trukket) {
            brevService.journalFørAvsluttetSøknadsBehandlingOgSendBrev(avsluttSøknadsBehandlingBody).fold(
                ifLeft = {
                    log.error("$loggtema: Kunne ikke sende brev for å avslutte søknadsbehandling")
                    return KunneIkkeAvslutteSøknadsBehandling.left()
                },
                ifRight = {
                    søknadService.avsluttSøknadsBehandling(avsluttSøknadsBehandlingBody).fold(
                        ifLeft = {
                            log.error("$loggtema: Kunne ikke avslutte søknadsbehandling")
                            it
                        },
                        ifRight = {
                            return it.right()
                        }
                    )
                }
            )
        }
        return KunneIkkeAvslutteSøknadsBehandling.left()
    }

    private fun opprettJournalpostOgOppgave(sakId: UUID, søknad: Søknad) {
        pdfGenerator.genererPdf(søknad.søknadInnhold).fold(
            {
                log.error("$it")
            },
            { pdfByteArray ->
                val fnr = søknad.søknadInnhold.personopplysninger.fnr
                dokArkiv.opprettJournalpost(
                    Journalpost.Søknadspost(
                        person = personOppslag.person(fnr).getOrElse {
                            log.error("Fant ikke person med gitt fødselsnummer")
                            throw RuntimeException("Kunne ikke finne person")
                        },
                        søknadInnhold = søknad.søknadInnhold,
                        pdf = pdfByteArray,
                        sakId = sakId.toString()
                    )
                ).fold(
                    {
                        log.error("$it")
                    },
                    { journalpostId ->
                        val aktørId: AktørId = personOppslag.aktørId(fnr).getOrElse {
                            log.error("Fant ikke aktør-id med gitt fødselsnummer")
                            throw RuntimeException("Kunne ikke finne aktørid")
                        }
                        oppgaveClient.opprettOppgave(
                            OppgaveConfig.Saksbehandling(
                                journalpostId = journalpostId,
                                sakId = sakId.toString(),
                                aktørId = aktørId
                            )
                        ).mapLeft {
                            log.error("$it")
                        }
                    }
                )
            }
        )
    }
}
