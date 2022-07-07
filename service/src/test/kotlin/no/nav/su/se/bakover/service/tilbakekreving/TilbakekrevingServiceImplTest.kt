// package no.nav.su.se.bakover.service.tilbakekreving
//
// import arrow.core.left
// import io.kotest.matchers.string.shouldContain
// import no.nav.su.se.bakover.common.UUID30
// import no.nav.su.se.bakover.common.periode.år
// import no.nav.su.se.bakover.domain.NavIdentBruker
// import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Kravgrunnlag
// import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.MottattKravgrunnlag
// import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.RåttKravgrunnlag
// import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrev
// import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingsvedtakForsendelseFeil
// import no.nav.su.se.bakover.test.fixedTidspunkt
// import no.nav.su.se.bakover.test.saksnummer
// import org.junit.jupiter.api.Test
// import org.junit.jupiter.api.assertThrows
// import org.mockito.kotlin.any
// import org.mockito.kotlin.doReturn
// import org.mockito.kotlin.mock
// import java.util.UUID
//
// internal class TilbakekrevingServiceImplTest {
//     @Test
//     fun `kaster exception hvis oversendelse av tilbakekrevingsvedtak feiler`() {
//         TilbakekrevingServiceImpl(
//             tilbakekrevingRepo = mock() {
//                 on { hentMottattKravgrunnlag() } doReturn listOf(
//                     MottattKravgrunnlag(
//                         avgjort = Tilbakekrev(
//                             id = UUID.randomUUID(),
//                             opprettet = fixedTidspunkt,
//                             sakId = UUID.randomUUID(),
//                             revurderingId = UUID.randomUUID(),
//                             periode = år(2021),
//
//                             ),
//                         kravgrunnlag = RåttKravgrunnlag("xml"),
//                         kravgrunnlagMottatt = fixedTidspunkt,
//                     ),
//                 )
//             },
//             tilbakekrevingClient = mock {
//                 on { sendTilbakekrevingsvedtak(any()) } doReturn TilbakekrevingsvedtakForsendelseFeil.left()
//             },
//             vedtakService =,
//             brevService =,
//             sessionFactory =,
//             clock =,
//         ).let {
//             assertThrows<RuntimeException> {
//                 it.sendTilbakekrevingsvedtak {
//                     Kravgrunnlag(
//                         saksnummer = saksnummer,
//                         kravgrunnlagId = "1234",
//                         vedtakId = "1234",
//                         kontrollfelt = "1234",
//                         status = Kravgrunnlag.KravgrunnlagStatus.NY,
//                         behandler = NavIdentBruker.Saksbehandler("sverre"),
//                         utbetalingId = UUID30.randomUUID(),
//                         grunnlagsperioder = listOf(),
//                     )
//                 }
//             }.also {
//                 it.message shouldContain "Feil ved oversendelse av tilbakekrevingsvedtak for tilbakekrevingsbehandling"
//             }
//         }
//     }
// }
