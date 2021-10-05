package no.nav.su.se.bakover.client.oppdrag.simulering

import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.client.oppdrag.avstemming.sakId
import no.nav.su.se.bakover.client.oppdrag.avstemming.saksnummer
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
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
            utbetaling = Utbetaling.UtbetalingForSimulering(
                saksnummer = saksnummer,
                sakId = sakId,
                utbetalingslinjer = nonEmptyListOf(
                    Utbetalingslinje.Ny(
                        fraOgMed = 1.januar(2020),
                        tilOgMed = 31.januar(2020),
                        beløp = 10,
                        forrigeUtbetalingslinjeId = eksisterendeOppdragslinjeid,
                        uføregrad = Uføregrad.parse(50),
                    ),
                ),
                fnr = Fnr("12345678910"),
                type = Utbetaling.UtbetalingsType.NY,

                behandler = NavIdentBruker.Saksbehandler("Z123"),
                avstemmingsnøkkel = Avstemmingsnøkkel(),
            ),
        ).build().request

        val skjema = this::class.java.getResource("/simulering/simulerFpServiceServiceTypes.xsd").toURI()
        val s: Schema = SchemaFactory
            .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(File(skjema))

        val marshaller = marshaller
        marshaller.schema = s
        marshaller.marshal(
            JAXBElement(QName("", "request"), SimulerBeregningRequest::class.java, simuleringRequest),
            DefaultHandler()
        )
    }
}
