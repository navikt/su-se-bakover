package no.nav.su.se.bakover.service.historisk

import arrow.core.Either
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.client.historisk.CountResponse
import no.nav.su.se.bakover.client.historisk.KolonnebeskrivelseDto
import no.nav.su.se.bakover.client.historisk.SchemaDto
import no.nav.su.se.bakover.client.historisk.SupstonadHistoriskClient
import no.nav.su.se.bakover.client.historisk.UttrekkResponse
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.domain.historisk.HistoriskImport
import no.nav.su.se.bakover.domain.historisk.HistoriskImportRepo
import no.nav.su.se.bakover.domain.historisk.HistoriskRådataSide
import no.nav.su.se.bakover.domain.historisk.NyHistoriskTabellimport
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SupstonadHistoriskServiceTest {

    @Test
    fun `importerer alle tabeller sidevis og bevarer null`() {
        val vedtak = "INFOTRYGD_SUQ.T_VEDTAK"
        val skjema = SupstonadHistoriskService.TABELLER_SOM_SKAL_IMPORTERES.associateWith { listOf("ID") }
        val client = FakeHistoriskClient(
            tabeller = skjema,
            antall = mapOf(vedtak to 2L),
            uttrekk = mutableMapOf(
                vedtak to ArrayDeque(
                    listOf(
                        uttrekk(iterator = "side-2", innhold = listOf(listOf("1"))),
                        uttrekk(iterator = "", innhold = listOf(listOf(null))),
                    ),
                ),
            ),
        )
        val repo = HistoriskImportRepoFake()

        val resultat = SupstonadHistoriskService(client, repo).importerAlleTabeller(sideStørrelse = 1)
            .shouldBeRight()

        resultat.importerteTabeller shouldBe SupstonadHistoriskService.TABELLER_SOM_SKAL_IMPORTERES.size
        resultat.importerteRader shouldBe 2
        repo.fullført shouldBe true
        repo.lagredeSider.map { it.side } shouldBe listOf(0, 1)
        repo.lagredeSider.map { it.rader.single() } shouldBe listOf(
            mapOf("ID" to "1"),
            mapOf("ID" to null),
        )
    }

    @Test
    fun `markerer importen som feilet når en rad ikke matcher skjemaet`() {
        val vedtak = "INFOTRYGD_SUQ.T_VEDTAK"
        val skjema = SupstonadHistoriskService.TABELLER_SOM_SKAL_IMPORTERES.associateWith { listOf("ID") }
        val client = FakeHistoriskClient(
            tabeller = skjema,
            antall = mapOf(vedtak to 1L),
            uttrekk = mutableMapOf(
                vedtak to ArrayDeque(
                    listOf(uttrekk(iterator = "", innhold = listOf(listOf("1", "for-mange-felt")))),
                ),
            ),
        )
        val repo = HistoriskImportRepoFake()

        val feil = SupstonadHistoriskService(client, repo).importerAlleTabeller().shouldBeLeft()

        feil shouldBe KunneIkkeImportereHistoriskeData.UgyldigRadbredde(
            tabellnavn = vedtak,
            side = 0,
            radnummer = 0,
            forventet = 1,
            faktisk = 2,
        )
        repo.feilbeskrivelse?.contains("UgyldigRadbredde") shouldBe true
        repo.lagredeSider shouldBe emptyList()
    }

    @Test
    fun `feiler dersom manglende tabeller i kilde`() {
        val ufullstendigSkjema = SupstonadHistoriskService.TABELLER_SOM_SKAL_IMPORTERES
            .drop(1).associateWith { listOf("ID") }
        val client = FakeHistoriskClient(
            tabeller = ufullstendigSkjema,
            antall = emptyMap(),
            uttrekk = mutableMapOf(),
        )
        val repo = HistoriskImportRepoFake()

        val feil = SupstonadHistoriskService(client, repo).importerAlleTabeller().shouldBeLeft()
        feil.shouldBeInstanceOf<KunneIkkeImportereHistoriskeData.ManglendeTabeller>()
    }

    @Test
    fun `feiler ved ugyldig sidestørrelse`() {
        val skjema = SupstonadHistoriskService.TABELLER_SOM_SKAL_IMPORTERES.associateWith { listOf("ID") }
        val client = FakeHistoriskClient(tabeller = skjema, antall = emptyMap(), uttrekk = mutableMapOf())
        val repo = HistoriskImportRepoFake()

        SupstonadHistoriskService(client, repo).importerAlleTabeller(sideStørrelse = 0).shouldBeLeft()
            .shouldBeInstanceOf<KunneIkkeImportereHistoriskeData.UgyldigSidestørrelse>()
        SupstonadHistoriskService(client, repo).importerAlleTabeller(sideStørrelse = 99_999).shouldBeLeft()
            .shouldBeInstanceOf<KunneIkkeImportereHistoriskeData.UgyldigSidestørrelse>()
    }

    @Test
    fun `feiler dersom iterator står stille`() {
        val vedtak = "INFOTRYGD_SUQ.T_VEDTAK"
        val skjema = SupstonadHistoriskService.TABELLER_SOM_SKAL_IMPORTERES.associateWith { listOf("ID") }
        val client = FakeHistoriskClient(
            tabeller = skjema,
            antall = mapOf(vedtak to 2L),
            uttrekk = mutableMapOf(
                vedtak to ArrayDeque(
                    listOf(
                        uttrekk(iterator = "abc", innhold = listOf(listOf("1"))),
                        uttrekk(iterator = "abc", innhold = listOf(listOf("2"))),
                    ),
                ),
            ),
        )
        val repo = HistoriskImportRepoFake()

        val feil = SupstonadHistoriskService(client, repo).importerAlleTabeller(sideStørrelse = 1).shouldBeLeft()
        feil.shouldBeInstanceOf<KunneIkkeImportereHistoriskeData.IteratorSyklus>()
        repo.feilbeskrivelse shouldNotBe null
    }

    @Test
    fun `oppdager iterator-syklus A til B til A`() {
        val vedtak = "INFOTRYGD_SUQ.T_VEDTAK"
        val skjema = SupstonadHistoriskService.TABELLER_SOM_SKAL_IMPORTERES.associateWith { listOf("ID") }
        val client = FakeHistoriskClient(
            tabeller = skjema,
            antall = mapOf(vedtak to 3L),
            uttrekk = mutableMapOf(
                vedtak to ArrayDeque(
                    listOf(
                        uttrekk(iterator = "A", innhold = listOf(listOf("1"))),
                        uttrekk(iterator = "B", innhold = listOf(listOf("2"))),
                        uttrekk(iterator = "A", innhold = listOf(listOf("3"))),
                    ),
                ),
            ),
        )
        val repo = HistoriskImportRepoFake()

        val feil = SupstonadHistoriskService(client, repo).importerAlleTabeller(sideStørrelse = 1).shouldBeLeft()
        feil.shouldBeInstanceOf<KunneIkkeImportereHistoriskeData.IteratorSyklus>()
    }

    @Test
    fun `fortsetter fra checkpoint ved pågående import`() {
        val vedtak = "INFOTRYGD_SUQ.T_VEDTAK"
        val skjema = SupstonadHistoriskService.TABELLER_SOM_SKAL_IMPORTERES.associateWith { listOf("ID") }
        val repo = HistoriskImportRepoFake()

        val eksisterendeImportId = UUID.fromString("b144be5a-4225-46b0-bf9a-e00649cc87cd")
        repo.settPågåendeImport(
            HistoriskImport(
                id = eksisterendeImportId,
                status = HistoriskImport.Status.PÅGÅR,
                tabeller = SupstonadHistoriskService.TABELLER_SOM_SKAL_IMPORTERES.sorted().map {
                    HistoriskImport.Tabell(
                        tabellnavn = it,
                        status = if (it == vedtak) HistoriskImport.Status.PÅGÅR else HistoriskImport.Status.FULLFØRT,
                        forventetAntall = if (it == vedtak) 1L else 0L,
                        importertAntall = 0,
                        nesteIterator = null,
                        nesteSide = 0,
                        kolonner = listOf("ID"),
                    )
                },
            ),
        )

        val client = FakeHistoriskClient(
            tabeller = skjema,
            antall = mapOf(vedtak to 1L),
            uttrekk = mutableMapOf(
                vedtak to ArrayDeque(listOf(uttrekk(iterator = "", innhold = listOf(listOf("42"))))),
            ),
        )

        val resultat = SupstonadHistoriskService(client, repo).importerAlleTabeller().shouldBeRight()
        resultat.importId shouldBe eksisterendeImportId
        repo.fullført shouldBe true
    }

    @Test
    fun `feiler ved antallsavvik mellom tellRader og faktisk mottatte rader`() {
        val vedtak = "INFOTRYGD_SUQ.T_VEDTAK"
        val skjema = SupstonadHistoriskService.TABELLER_SOM_SKAL_IMPORTERES.associateWith { listOf("ID") }
        val client = FakeHistoriskClient(
            tabeller = skjema,
            antall = mapOf(vedtak to 3L),
            uttrekk = mutableMapOf(
                vedtak to ArrayDeque(
                    listOf(uttrekk(iterator = "", innhold = listOf(listOf("1"), listOf("2")))),
                ),
            ),
        )
        val repo = HistoriskImportRepoFake()

        val feil = SupstonadHistoriskService(client, repo).importerAlleTabeller().shouldBeLeft()
        feil.shouldBeInstanceOf<KunneIkkeImportereHistoriskeData.Antallsavvik>()
    }

    private fun uttrekk(iterator: String, innhold: List<List<String?>>) = UttrekkResponse(
        iterator = iterator,
        schema = SchemaDto(listOf(KolonnebeskrivelseDto("ID"))),
        innhold = innhold,
    )
}

private class FakeHistoriskClient(
    private val tabeller: Map<String, List<String>>,
    private val antall: Map<String, Long>,
    private val uttrekk: MutableMap<String, ArrayDeque<UttrekkResponse>>,
) : SupstonadHistoriskClient {
    override fun tellRader(tabellnavn: String): Either<ClientError, CountResponse> =
        CountResponse(antall.getOrDefault(tabellnavn, 0)).right()

    override fun hentUttrekk(
        tabellnavn: String,
        antallRader: Long,
        iterator: String?,
    ): Either<ClientError, UttrekkResponse> = uttrekk.getValue(tabellnavn).removeFirst().right()

    override fun hentTabeller(): Either<ClientError, Map<String, List<String>>> = tabeller.right()
}

private class HistoriskImportRepoFake : HistoriskImportRepo {
    private val importId = UUID.fromString("b144be5a-4225-46b0-bf9a-e00649cc87cd")
    private var import: HistoriskImport? = null
    val lagredeSider = mutableListOf<HistoriskRådataSide>()
    var fullført = false
        private set
    var feilbeskrivelse: String? = null
        private set

    fun settPågåendeImport(historiskImport: HistoriskImport) {
        import = historiskImport
    }

    override fun hentPågåendeImport(): HistoriskImport? = import?.takeIf {
        it.status == HistoriskImport.Status.PÅGÅR
    }

    override fun opprettImport(tabeller: List<NyHistoriskTabellimport>): HistoriskImport {
        return HistoriskImport(
            id = importId,
            status = HistoriskImport.Status.PÅGÅR,
            tabeller = tabeller.map {
                HistoriskImport.Tabell(
                    tabellnavn = it.tabellnavn,
                    status = if (it.forventetAntall == 0L) {
                        HistoriskImport.Status.FULLFØRT
                    } else {
                        HistoriskImport.Status.PÅGÅR
                    },
                    forventetAntall = it.forventetAntall,
                    importertAntall = 0,
                    nesteIterator = null,
                    nesteSide = 0,
                    kolonner = it.kolonner,
                )
            },
        ).also { import = it }
    }

    override fun lagreSide(side: HistoriskRådataSide): HistoriskImport.Tabell {
        lagredeSider.add(side)
        val eksisterende = import!!.tabeller.single { it.tabellnavn == side.tabellnavn }
        val oppdatert = eksisterende.copy(
            status = if (side.nesteIterator == null) {
                HistoriskImport.Status.FULLFØRT
            } else {
                HistoriskImport.Status.PÅGÅR
            },
            importertAntall = eksisterende.importertAntall + side.rader.size,
            nesteIterator = side.nesteIterator,
            nesteSide = side.side + 1,
        )
        import = import!!.copy(
            tabeller = import!!.tabeller.map { if (it.tabellnavn == side.tabellnavn) oppdatert else it },
        )
        return oppdatert
    }

    override fun fullførImport(importId: UUID) {
        fullført = true
        import = import!!.copy(status = HistoriskImport.Status.FULLFØRT)
    }

    override fun markerFeilet(importId: UUID, beskrivelse: String) {
        feilbeskrivelse = beskrivelse
        import = import!!.copy(status = HistoriskImport.Status.FEILET)
    }
}
