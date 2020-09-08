package no.nav.su.se.bakover.client.oppdrag.simulering

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest
import org.junit.jupiter.api.Test
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.bind.JAXBContext
import javax.xml.bind.JAXBElement
import javax.xml.bind.Marshaller
import javax.xml.namespace.QName
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory

internal class SimuleringRequestBuilderValidationTest {
    val jaxbContext = JAXBContext.newInstance(no.nav.system.os.tjenester.simulerfpservice.simulerfpservicegrensesnitt.SimulerBeregningRequest::class.java)
    val marshaller = jaxbContext.createMarshaller().apply {
        setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
    }

    @Test
    fun `valider soap xml mot xsd skjema`() {
        val eksisterendeOppdragslinjeid = UUID30.randomUUID()
        val simuleringRequest = SimuleringRequestBuilder(
            utbetaling = Utbetaling(
                behandlingId = UUID.randomUUID(),
                utbetalingslinjer = listOf(
                    Utbetalingslinje(
                        fom = 1.januar(2020),
                        tom = 14.januar(2020),
                        beløp = 10,
                        forrigeUtbetalingslinjeId = eksisterendeOppdragslinjeid
                    )
                ),
                oppdragId = UUID30.randomUUID()
            ),
            simuleringGjelder = Fnr("01010198765")
        ).build().request

        val skjema = this::class.java.getResource("/simulering/simulerFpServiceServiceTypes.xsd").toURI()
        val s: Schema = SchemaFactory
            .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(File(skjema))

        val marshaller = marshaller
        marshaller.schema = s
        marshaller.marshal(JAXBElement(QName("", "request"), SimulerBeregningRequest::class.java, simuleringRequest), DefaultHandler())
    }
}
