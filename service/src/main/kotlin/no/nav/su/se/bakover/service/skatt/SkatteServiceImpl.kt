package no.nav.su.se.bakover.service.skatt

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.journalføring.Fagsystem
import dokument.domain.journalføring.QueryJournalpostClient
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.common.tid.toRange
import no.nav.su.se.bakover.domain.sak.SakService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import person.domain.PersonService
import vilkår.skatt.application.FrioppslagSkattRequest
import vilkår.skatt.application.GenererSkattPdfRequest
import vilkår.skatt.application.KunneIkkeGenerereSkattePdfOgJournalføre
import vilkår.skatt.application.KunneIkkeHenteOgLagePdfAvSkattegrunnlag
import vilkår.skatt.application.SkatteService
import vilkår.skatt.domain.KunneIkkeHenteSkattemelding
import vilkår.skatt.domain.Skattegrunnlag
import vilkår.skatt.domain.Skatteoppslag
import java.time.Clock
import java.time.Year
import java.util.UUID

/**
 * Har som ansvar å hente skattegrunnlaget, og gjøre noe videre med denne
 *
 * * TODO - på sikt vil vi at denne skal være i skattemodulen
 */
class SkatteServiceImpl(
    private val skatteClient: Skatteoppslag,
    private val skattDokumentService: SkattDokumentService,
    private val personService: PersonService,
    private val sakService: SakService,
    private val journalpostClient: QueryJournalpostClient,
    val clock: Clock,
) : SkatteService {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun hentSamletSkattegrunnlag(
        fnr: Fnr,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Skattegrunnlag = Skattegrunnlag(
        id = UUID.randomUUID(),
        fnr = fnr,
        hentetTidspunkt = Tidspunkt.now(clock),
        saksbehandler = saksbehandler,
        årsgrunnlag = skatteClient.hentSamletSkattegrunnlag(fnr, Year.now(clock).minusYears(1))
            .hentMestGyldigeSkattegrunnlag(),
        årSpurtFor = Year.now(clock).minusYears(1).toRange(),
    )

    override fun hentSamletSkattegrunnlagForÅr(
        fnr: Fnr,
        saksbehandler: NavIdentBruker.Saksbehandler,
        yearRange: YearRange,
    ): Skattegrunnlag = Skattegrunnlag(
        id = UUID.randomUUID(),
        fnr = fnr,
        hentetTidspunkt = Tidspunkt.now(clock),
        saksbehandler = saksbehandler,
        årsgrunnlag = skatteClient.hentSamletSkattegrunnlagForÅrsperiode(fnr, yearRange)
            .map { it.hentMestGyldigeSkattegrunnlag() }.toNonEmptyList(),
        årSpurtFor = yearRange,
    )

    override fun hentOgLagSkattePdf(request: FrioppslagSkattRequest): Either<KunneIkkeHenteOgLagePdfAvSkattegrunnlag, PdfA> {
        return hentSkattegrunnlag(request).getOrElse {
            return KunneIkkeHenteOgLagePdfAvSkattegrunnlag.KunneIkkeHenteSkattemelding(it).left()
        }.let {
            skattDokumentService.genererSkattePdf(
                GenererSkattPdfRequest(
                    skattegrunnlagSøkers = it.first,
                    skattegrunnlagEps = it.second,
                    begrunnelse = request.begrunnelse,
                    sakstype = request.sakstype,
                    fagsystemId = request.fagsystemId,
                ),
            )
        }
    }

    override fun hentLagOgJournalførSkattePdf(
        request: FrioppslagSkattRequest,
    ): Either<KunneIkkeGenerereSkattePdfOgJournalføre, PdfA> {
        val verifisering = when (request.sakstype) {
            Sakstype.ALDER -> verifiserRequestMedAlder(request)
            Sakstype.UFØRE -> verifiserRequestMedUføre(request)
        }

        return verifisering.fold(
            ifLeft = { it.left() },
            ifRight = {
                hentSkattegrunnlag(request).getOrElse {
                    return KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedHentingAvSkattemelding(it).left()
                }.let {
                    log.info("Hentet skattegrunnlag for sakstype ${request.sakstype} med fagsystemId ${request.fagsystemId}")
                    skattDokumentService.genererSkattePdfOgJournalfør(
                        GenererSkattPdfRequest(
                            skattegrunnlagSøkers = it.first,
                            skattegrunnlagEps = it.second,
                            begrunnelse = request.begrunnelse,
                            sakstype = request.sakstype,
                            fagsystemId = request.fagsystemId,
                        ),
                    )
                }
            },
        )
    }

    /**
     * Verifiserer 4 ting:
     * 1. At sakstype er uføre
     * 2. At fnr finnes i PDL, og at saksbehandler har tilgang til personen
     * 3. At fnr i saken er lik fnr i request
     * 4. At epsFnr finnes i PDL, og at saksbehandler har tilgang til EPS dersom den er satt
     */
    private fun verifiserRequestMedUføre(request: FrioppslagSkattRequest): Either<KunneIkkeGenerereSkattePdfOgJournalføre, Unit> {
        val saksnummer = Either.catch {
            Saksnummer(request.fagsystemId.toLong())
        }.fold(
            ifLeft = { return KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedKonverteringAvFagsystemIdTilSaksnummer.left() },
            ifRight = {
                it
            },
        )

        val uføresak = sakService.hentSak(saksnummer).getOrElse {
            return KunneIkkeGenerereSkattePdfOgJournalføre.FantIkkeSak.left()
        }.let {
            if (it.type != Sakstype.UFØRE) {
                return KunneIkkeGenerereSkattePdfOgJournalføre.SakstypeErIkkeDenSammeSomForespurt(
                    it.type,
                    request.sakstype,
                ).left()
            }
            it
        }

        request.fnr?.let {
            val person = personService.hentPerson(it)
                .getOrElse { return KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedHentingAvPerson(it).left() }

            if (uføresak.fnr != person.ident.fnr) {
                return KunneIkkeGenerereSkattePdfOgJournalføre.FnrPåSakErIkkeLikFnrViFikkFraPDL.left()
            }

            if (person.ident.fnr != it) {
                return KunneIkkeGenerereSkattePdfOgJournalføre.FikkTilbakeEtAnnetFnrFraPdlEnnDetSomBleSendtInn.left()
            }
        }

        request.epsFnr?.let {
            val person = personService.hentPerson(it)
                .getOrElse { return KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedHentingAvPerson(it).left() }

            if (person.ident.fnr != it) {
                return KunneIkkeGenerereSkattePdfOgJournalføre.FikkTilbakeEtAnnetFnrFraPdlEnnDetSomBleSendtInn.left()
            }
        }

        return Unit.right()
    }

    /**
     * Verifiserer 4 ting:
     * 1. At vi ikke har en sak med angitt saksnummer
     * 2. At fnr finnes i PDL, og at saksbehandler har tilgang til personen
     * 3. At saksnummeret finnes i Joark
     * 4. At epsFnr finnes i PDL, og at saksbehandler har tilgang til EPS dersom den er satt
     */
    private fun verifiserRequestMedAlder(request: FrioppslagSkattRequest): Either<KunneIkkeGenerereSkattePdfOgJournalføre, Unit> {
        request.fnr?.let {
            val person = personService.hentPerson(it)
                .getOrElse { return KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedHentingAvPerson(it).left() }

            if (person.ident.fnr != it) {
                return KunneIkkeGenerereSkattePdfOgJournalføre.FikkTilbakeEtAnnetFnrFraPdlEnnDetSomBleSendtInn.left()
            }
        }
        request.epsFnr?.let {
            val person = personService.hentPerson(it)
                .getOrElse { return KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedHentingAvPerson(it).left() }

            if (person.ident.fnr != it) {
                return KunneIkkeGenerereSkattePdfOgJournalføre.FikkTilbakeEtAnnetFnrFraPdlEnnDetSomBleSendtInn.left()
            }
        }

        journalpostClient.finnesFagsak(request.fagsystemId, Fagsystem.INFOTRYGD).fold(
            ifLeft = {
                return KunneIkkeGenerereSkattePdfOgJournalføre.FeilVedVerifiseringAvFagsakMotJoark.left()
            },
            ifRight = {
                if (!it) {
                    return KunneIkkeGenerereSkattePdfOgJournalføre.FantIkkeAlderssak.left()
                }
            },
        )
        return Unit.right()
    }

    private fun hentSkattegrunnlag(request: FrioppslagSkattRequest): Either<KunneIkkeHenteSkattemelding, Pair<Skattegrunnlag?, Skattegrunnlag?>> {
        val skattegrunnlagSøkers = if (request.fnr != null) {
            Skattegrunnlag(
                id = UUID.randomUUID(),
                fnr = request.fnr!!,
                hentetTidspunkt = Tidspunkt.now(clock),
                saksbehandler = request.saksbehandler,
                årsgrunnlag = skatteClient.hentSamletSkattegrunnlag(request.fnr!!, request.år)
                    .hentMestGyldigeSkattegrunnlagEllerFeil()
                    .getOrElse { return it.tilKunneIkkeHenteSkattemelding().left() },
                årSpurtFor = request.år.toRange(),
            )
        } else {
            null
        }

        val skattegrunnlagEps = if (request.epsFnr != null) {
            Skattegrunnlag(
                id = UUID.randomUUID(),
                fnr = request.epsFnr!!,
                hentetTidspunkt = Tidspunkt.now(clock),
                saksbehandler = request.saksbehandler,
                årsgrunnlag = skatteClient.hentSamletSkattegrunnlag(request.epsFnr!!, request.år)
                    .hentMestGyldigeSkattegrunnlagEllerFeil()
                    .getOrElse { return it.tilKunneIkkeHenteSkattemelding().left() },
                årSpurtFor = request.år.toRange(),
            )
        } else {
            null
        }

        return Pair(skattegrunnlagSøkers, skattegrunnlagEps).right()
    }
}
