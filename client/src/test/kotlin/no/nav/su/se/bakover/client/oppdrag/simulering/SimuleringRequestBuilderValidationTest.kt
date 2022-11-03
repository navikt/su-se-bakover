package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.client.oppdrag.avstemming.sakId
import no.nav.su.se.bakover.client.oppdrag.avstemming.saksnummer
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerUtbetalingForPeriode
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.utbetalingslinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest
import org.junit.jupiter.api.Test
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import javax.xml.XMLConstants
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBElement
import javax.xml.bind.Marshaller
import javax.xml.namespace.QName
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory

internal class SimuleringRequestBuilderValidationTest {
    private val jaxbContext: JAXBContext =
        JAXBContext.newInstance(no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest::class.java)
    private val marshaller: Marshaller = jaxbContext.createMarshaller().apply {
        setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
    }

    @Test
    fun `valider soap xml mot xsd skjema`() {
        val eksisterendeOppdragslinjeid = UUID30.randomUUID()
        val simuleringRequest = SimuleringRequestBuilder(
            request = SimulerUtbetalingForPeriode(
                utbetaling = Utbetaling.UtbetalingForSimulering(
                    opprettet = fixedTidspunkt,
                    sakId = sakId,
                    saksnummer = saksnummer,
                    fnr = Fnr("12345678910"),
                    utbetalingslinjer = nonEmptyListOf(
                        utbetalingslinje(
                            periode = januar(2020),
                            beløp = 10,
                            forrigeUtbetalingslinjeId = eksisterendeOppdragslinjeid,
                        ),
                    ),
                    behandler = NavIdentBruker.Saksbehandler("Z123"),
                    avstemmingsnøkkel = Avstemmingsnøkkel(opprettet = fixedTidspunkt),
                    sakstype = Sakstype.UFØRE,
                ),
                simuleringsperiode = januar(2020),
            ),
        ).build().request

        val skjema = this::class.java.getResource("/simulering/simulerFpServiceServiceTypes.xsd")!!.toURI()
        val s: Schema = SchemaFactory
            .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(File(skjema))

        val marshaller = marshaller
        marshaller.schema = s
        marshaller.marshal(
            JAXBElement(QName("", "request"), SimulerBeregningRequest::class.java, simuleringRequest),
            DefaultHandler(),
        )
    }
}
