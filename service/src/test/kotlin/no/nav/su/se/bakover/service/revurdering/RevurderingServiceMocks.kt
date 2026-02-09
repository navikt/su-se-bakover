package no.nav.su.se.bakover.service.revurdering

import arrow.core.right
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.fritekst.Fritekst
import no.nav.su.se.bakover.domain.fritekst.FritekstService
import no.nav.su.se.bakover.domain.fritekst.FritekstType
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.domain.mottaker.MottakerService
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.revurdering.opphør.AnnullerKontrollsamtaleVedOpphørService
import no.nav.su.se.bakover.domain.revurdering.repo.RevurderingRepo
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.service.statistikk.SakStatistikkService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.defaultMock
import no.nav.su.se.bakover.test.dokumentUtenMetadataVedtak
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.vedtak.application.FerdigstillVedtakService
import no.nav.su.se.bakover.vedtak.application.VedtakService
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import person.domain.IdentClient
import person.domain.PersonService
import satser.domain.SatsFactory
import økonomi.application.utbetaling.UtbetalingService
import java.time.Clock
import java.util.UUID

internal data class RevurderingServiceMocks(
    val utbetalingService: UtbetalingService = defaultMock(),
    val revurderingRepo: RevurderingRepo = defaultMock(),
    val oppgaveService: OppgaveService = defaultMock(),
    val personService: PersonService = defaultMock(),
    val identClient: IdentClient = defaultMock(),
    val brevService: BrevService = mock {
        on { lagDokumentPdf(any(), anyOrNull()) } doReturn dokumentUtenMetadataVedtak().right()
    },
    val mottakerService: MottakerService = mock {
        on { hentMottaker(any(), any(), anyOrNull()) } doReturn null.right()
    },
    val vedtakService: VedtakService = defaultMock(),
    val ferdigstillVedtakService: FerdigstillVedtakService = defaultMock(),
    val sakService: SakService = defaultMock(),
    val annullerKontrollsamtaleService: AnnullerKontrollsamtaleVedOpphørService = defaultMock(),
    val sessionFactory: SessionFactory = TestSessionFactory(),
    val observer: StatistikkEventObserver = mock(),
    val klageRepo: KlageRepo = mock(),
    val clock: Clock = TikkendeKlokke(),
    val satsFactory: SatsFactory = satsFactoryTestPåDato(),
    val fritekstService: FritekstService = mock {
        on { hentFritekst(any(), any(), anyOrNull()) } doReturn Fritekst(
            referanseId = UUID.randomUUID(),
            type = FritekstType.VEDTAKSBREV_REVURDERING,
            fritekst = "",
        ).right()
    },
    val sakStatistikkService: SakStatistikkService = mock(),
) {
    val revurderingService = RevurderingServiceImpl(
        utbetalingService = utbetalingService,
        revurderingRepo = revurderingRepo,
        oppgaveService = oppgaveService,
        personService = personService,
        brevService = brevService,
        mottakerService = mottakerService,
        clock = clock,
        vedtakService = vedtakService,
        annullerKontrollsamtaleService = annullerKontrollsamtaleService,
        sessionFactory = sessionFactory,
        formuegrenserFactory = formuegrenserFactoryTestPåDato(),
        sakService = sakService,
        satsFactory = satsFactory,
        sakStatistikkService = sakStatistikkService,
        klageRepo = klageRepo,
        fritekstService = fritekstService,
    ).apply { addObserver(observer) }

    fun all() = listOf(
        vedtakService,
        utbetalingService,
        revurderingRepo,
        oppgaveService,
        personService,
        identClient,
        brevService,
        mottakerService,
        ferdigstillVedtakService,
        sakService,
        annullerKontrollsamtaleService,
    ).toTypedArray()

    fun verifyNoMoreInteractions() {
        org.mockito.kotlin.verifyNoMoreInteractions(
            *all(),
        )
    }
}
