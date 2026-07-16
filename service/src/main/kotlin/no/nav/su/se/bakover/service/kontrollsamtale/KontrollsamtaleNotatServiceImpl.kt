package no.nav.su.se.bakover.service.kontrollsamtale

import KontrollsamtaleNotatVedlegg
import KontrollsamtaleNotatVedleggRepo
import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.dokument.infrastructure.client.PdfGenerator
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollnotatPdfInnhold
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotat
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotatRepo
import no.nav.su.se.bakover.domain.kontrollnotat.kontrollnotatInnhold.KontrollnotatInnhold
import no.nav.su.se.bakover.domain.sak.SakService
import org.slf4j.LoggerFactory
import person.domain.PersonService
import java.time.Clock
import java.util.UUID

class KontrollsamtaleNotatServiceImpl(
    private val sakService: SakService,
    private val personService: PersonService,
    private val repository: KontrollsamtaleNotatRepo,
    private val vedleggRepository: KontrollsamtaleNotatVedleggRepo,
    private val pdfGenerator: PdfGenerator,
    private val clock: Clock,

) : KontrollsamtaleNotatService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lagre(
        sakId: UUID,
        kontrollsamtaleNotat: KontrollsamtaleNotat,
        sessionContext: SessionContext?,
    ) {
        repository.lagre(
            kontrollsamtaleNotat = kontrollsamtaleNotat,
            sakId = sakId,
            sessionContext = sessionContext,
        )
    }

    override fun hentKontrollsamtaleNotat(sakId: UUID): Either<KontrollsamtaleNotatService.FantIkkeKontrollnotat, KontrollsamtaleNotat> {
        return repository.hentKontrollsamtaleNotat(sakId)?.right()
            ?: KontrollsamtaleNotatService.FantIkkeKontrollnotat.left()
    }

    override fun leggTilVedlegg(
        sakId: UUID,
        filnavn: String,
        mimeType: String,
        innhold: ByteArray,
    ): Either<KontrollsamtaleNotatService.KontrollsamtaleNotatVedleggFeil, KontrollsamtaleNotatVedlegg> {
        val kontrollsamtaleNotat = repository.hentKontrollsamtaleNotat(sakId)
            ?: return KontrollsamtaleNotatService.KontrollsamtaleNotatVedleggFeil.FantIkkeKontrollnotat.left()

        val vedlegg = KontrollsamtaleNotatVedlegg(
            id = UUID.randomUUID(),
            kontrollsamtaleNotatId = kontrollsamtaleNotat.id,
            filnavn = filnavn,
            mimeType = mimeType,
            innhold = innhold,
            opprettet = Tidspunkt.now(clock),
        )

        vedleggRepository.leggTil(vedlegg)

        return vedlegg.right()
    }

    override fun hentVedlegg(
        sakId: UUID,
    ): Either<KontrollsamtaleNotatService.KontrollsamtaleNotatVedleggFeil, List<KontrollsamtaleNotatVedlegg>> {
        val kontrollsamtaleNotat = repository.hentKontrollsamtaleNotat(sakId)
            ?: return KontrollsamtaleNotatService.KontrollsamtaleNotatVedleggFeil.FantIkkeKontrollnotat.left()
        return vedleggRepository.hentForKontrollsamtaleNotat(kontrollsamtaleNotat.id).right()
    }

    override fun slettVedlegg(
        sakId: UUID,
        vedleggId: UUID,
    ): Either<KontrollsamtaleNotatService.KontrollsamtaleNotatVedleggFeil, Unit> {
        val kontrollsamtaleNotat = repository.hentKontrollsamtaleNotat(sakId)
            ?: return KontrollsamtaleNotatService.KontrollsamtaleNotatVedleggFeil.FantIkkeKontrollnotat.left()

        val vedlegg = vedleggRepository.hent(vedleggId)
            ?: return KontrollsamtaleNotatService.KontrollsamtaleNotatVedleggFeil.FantIkkeVedlegg.left()

        if (vedlegg.kontrollsamtaleNotatId != kontrollsamtaleNotat.id) {
            return KontrollsamtaleNotatService.KontrollsamtaleNotatVedleggFeil.FantIkkeVedlegg.left()
        }

        vedleggRepository.slett(vedleggId)

        return Unit.right()
    }

    override fun hentKontrollsamtaleNotatPdf(sakId: UUID): Either<KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf, PdfA> {
        return sakService.hentSak(sakId).mapLeft {
            log.error("Hent kontrollnotat-PDF: Fant ikke sak")
            KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf.FantIkkeSak
        }.flatMap { sak ->
            personService.hentPerson(sak.fnr, sakstype = sak.type).mapLeft {
                log.error("Hent kontrollnotat-PDF: Fant ikke person")
                KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf.FantIkkePerson
            }.flatMap { person ->
                hentKontrollsamtaleNotat(sakId).mapLeft {
                    log.error("Hent kontrollnotat-PDF: Fant ikke kontrollnotat")
                    KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf.FantIkkeKontrollnotat
                }.flatMap { kontrollnotat ->
                    pdfGenerator.genererPdf(
                        pdfInnhold = KontrollnotatPdfInnhold.Companion.create(
                            saksnummer = sak.saksnummer,
                            sakstype = sak.type,
                            navn = person.navn,
                            kontrollnotat = KontrollnotatInnhold(
                                personligOppmøte = kontrollnotat.personligOppmøte,
                                fullmaktOgLegeerklæring = kontrollnotat.fullmaktOgLegeerklæring,
                                originalPass = kontrollnotat.originalPass,
                                gyldigPass = kontrollnotat.gyldigPass,
                                harVærtUtenlands = kontrollnotat.harVærtUtenlands,
                                utenlandsoppholdDatoer = kontrollnotat.utenlandsoppholdDatoer.map {
                                    "${it.utreiseDato} - ${it.innreiseDato}"
                                },
                                harPlanerOmUtenlandsreise = kontrollnotat.harPlanerOmUtenlandsreise,
                                planlagteUtenlandsreiseDatoer = kontrollnotat.planlagteUtenlandsreiseDatoer.map {
                                    "${it.utreiseDato} - ${it.innreiseDato}"
                                },
                                reiseDokumentasjon = kontrollnotat.reiseDokumentasjon,
                                økonomiskSituasjon = kontrollnotat.økonomiskSituasjon,
                                andreForhold = kontrollnotat.andreForhold,
                                skatteOpplysninger = kontrollnotat.skatteOpplysninger,
                                fritekst = kontrollnotat.fritekst,
                            ),
                            clock = clock,
                        ),
                    ).mapLeft {
                        log.error("Hent kontrollnotat-PDF: Kunne ikke generere PDF. Originalfeil: $it")
                        KontrollsamtaleNotatService.KunneIkkeLageKontrollnotatPdf.KunneIkkeLagePdf
                    }
                }
            }
        }
    }
}
