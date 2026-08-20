package no.nav.su.se.bakover.service.historisk

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import no.nav.su.se.bakover.client.historisk.KolonnebeskrivelseDto
import no.nav.su.se.bakover.client.historisk.SchemaDto
import no.nav.su.se.bakover.client.historisk.SupstonadHistoriskClientStub
import no.nav.su.se.bakover.client.historisk.UttrekkResponse
import no.nav.su.se.bakover.domain.historisk.HistoriskImport
import no.nav.su.se.bakover.domain.historisk.HistoriskImportOversikt
import no.nav.su.se.bakover.domain.historisk.HistoriskImportRepo
import no.nav.su.se.bakover.domain.historisk.HistoriskImportTabellOversikt
import no.nav.su.se.bakover.domain.historisk.HistoriskRådataSide
import no.nav.su.se.bakover.domain.historisk.InfotrygdTabeller
import no.nav.su.se.bakover.domain.historisk.NyHistoriskTabellimport
import no.nav.su.se.bakover.domain.historisk.SlettImportResultat
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SupstonadHistoriskServiceTest {

    @Test
    fun `seed oppretter en fullført og en feilet import`() {
        val repo = MultiImportRepoFake()
        seedHistoriskeImporterLokalt(repo)

        val importer = repo.hentAlleImporter()
        importer.size shouldBe 2
        importer.count { it.status == HistoriskImport.Status.FULLFØRT } shouldBe 1
        importer.count { it.status == HistoriskImport.Status.FEILET } shouldBe 1

        val fullført = importer.single { it.status == HistoriskImport.Status.FULLFØRT }
        fullført.tabeller.size shouldBe 16
        fullført.tabeller.all { it.status == HistoriskImport.Status.FULLFØRT } shouldBe true
        fullført.tabeller.all { it.importertAntall == it.forventetAntall } shouldBe true

        val feilet = importer.single { it.status == HistoriskImport.Status.FEILET }
        feilet.feilbeskrivelse shouldNotBe null
    }

    @Test
    fun `seed sletter eksisterende og seeder på nytt`() {
        val repo = MultiImportRepoFake()
        seedHistoriskeImporterLokalt(repo)
        val førsteGang = repo.hentAlleImporter().map { it.id }.toSet()

        seedHistoriskeImporterLokalt(repo)
        val andreGang = repo.hentAlleImporter().map { it.id }.toSet()

        andreGang.size shouldBe 2
        andreGang.intersect(førsteGang) shouldBe emptySet()
    }

    @Test
    fun `importerer alle tabeller sidevis og bevarer null`() {
        val vedtak = InfotrygdTabeller.T_VEDTAK
        val skjema = SupstonadHistoriskService.TABELLER_SOM_SKAL_IMPORTERES.associateWith { listOf("ID") }
        val client = SupstonadHistoriskClientStub(
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
        val vedtak = InfotrygdTabeller.T_VEDTAK
        val skjema = SupstonadHistoriskService.TABELLER_SOM_SKAL_IMPORTERES.associateWith { listOf("ID") }
        val client = SupstonadHistoriskClientStub(
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
        val client = SupstonadHistoriskClientStub(
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
        val client = SupstonadHistoriskClientStub(tabeller = skjema, antall = emptyMap(), uttrekk = mutableMapOf())
        val repo = HistoriskImportRepoFake()

        SupstonadHistoriskService(client, repo).importerAlleTabeller(sideStørrelse = 0).shouldBeLeft()
            .shouldBeInstanceOf<KunneIkkeImportereHistoriskeData.UgyldigSidestørrelse>()
        SupstonadHistoriskService(client, repo).importerAlleTabeller(sideStørrelse = 99_999).shouldBeLeft()
            .shouldBeInstanceOf<KunneIkkeImportereHistoriskeData.UgyldigSidestørrelse>()
    }

    @Test
    fun `feiler dersom iterator står stille`() {
        val vedtak = InfotrygdTabeller.T_VEDTAK
        val skjema = SupstonadHistoriskService.TABELLER_SOM_SKAL_IMPORTERES.associateWith { listOf("ID") }
        val client = SupstonadHistoriskClientStub(
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
        feil.shouldBeInstanceOf<KunneIkkeImportereHistoriskeData.IteratorLoop>()
        repo.feilbeskrivelse shouldNotBe null
    }

    @Test
    fun `oppdager iterator-syklus A til B til A`() {
        val vedtak = InfotrygdTabeller.T_VEDTAK
        val skjema = SupstonadHistoriskService.TABELLER_SOM_SKAL_IMPORTERES.associateWith { listOf("ID") }
        val client = SupstonadHistoriskClientStub(
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
        feil.shouldBeInstanceOf<KunneIkkeImportereHistoriskeData.IteratorLoop>()
    }

    @Test
    fun `avviser ny import hvis en allerede pågår`() {
        val eksisterendeImportId = UUID.fromString("b144be5a-4225-46b0-bf9a-e00649cc87cd")
        val repo = HistoriskImportRepoFake()
        repo.settPågåendeImport(
            HistoriskImport(
                id = eksisterendeImportId,
                status = HistoriskImport.Status.PÅGÅR,
                opprettet = fixedTidspunkt,
                tabeller = emptyList(),
            ),
        )
        val client = SupstonadHistoriskClientStub(tabeller = emptyMap(), antall = emptyMap(), uttrekk = mutableMapOf())

        val feil = SupstonadHistoriskService(client, repo).importerAlleTabeller().shouldBeLeft()
        feil.shouldBeInstanceOf<KunneIkkeImportereHistoriskeData.ImportPågår>()
        (feil as KunneIkkeImportereHistoriskeData.ImportPågår).importId shouldBe eksisterendeImportId
    }

    @Test
    fun `feiler ved antallsavvik mellom tellRader og faktisk mottatte rader`() {
        val vedtak = InfotrygdTabeller.T_VEDTAK
        val skjema = SupstonadHistoriskService.TABELLER_SOM_SKAL_IMPORTERES.associateWith { listOf("ID") }
        val client = SupstonadHistoriskClientStub(
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

    @Test
    fun `sletter fullført import`() {
        val repo = HistoriskImportRepoFake()
        val importId = UUID.fromString("b144be5a-4225-46b0-bf9a-e00649cc87cd")
        repo.settPågåendeImport(
            HistoriskImport(id = importId, status = HistoriskImport.Status.FULLFØRT, opprettet = fixedTidspunkt, tabeller = emptyList()),
        )
        val client = SupstonadHistoriskClientStub(tabeller = emptyMap(), antall = emptyMap(), uttrekk = mutableMapOf())

        SupstonadHistoriskService(client, repo).slettImport(importId).shouldBeRight()

        repo.hentPågåendeImport() shouldBe null
    }

    @Test
    fun `sletter feilet import`() {
        val repo = HistoriskImportRepoFake()
        val importId = UUID.fromString("b144be5a-4225-46b0-bf9a-e00649cc87cd")
        repo.settPågåendeImport(
            HistoriskImport(id = importId, status = HistoriskImport.Status.FEILET, opprettet = fixedTidspunkt, tabeller = emptyList()),
        )
        val client = SupstonadHistoriskClientStub(tabeller = emptyMap(), antall = emptyMap(), uttrekk = mutableMapOf())

        SupstonadHistoriskService(client, repo).slettImport(importId).shouldBeRight()

        repo.hentPågåendeImport() shouldBe null
    }

    @Test
    fun `kan ikke slette pågående import`() {
        val repo = HistoriskImportRepoFake()
        val importId = UUID.fromString("b144be5a-4225-46b0-bf9a-e00649cc87cd")
        repo.settPågåendeImport(
            HistoriskImport(id = importId, status = HistoriskImport.Status.PÅGÅR, opprettet = fixedTidspunkt, tabeller = emptyList()),
        )
        val client = SupstonadHistoriskClientStub(tabeller = emptyMap(), antall = emptyMap(), uttrekk = mutableMapOf())

        SupstonadHistoriskService(client, repo).slettImport(importId).shouldBeLeft()
            .shouldBeInstanceOf<KunneIkkeSletteImport.ImportPågår>()
    }

    private fun uttrekk(iterator: String, innhold: List<List<String?>>) = UttrekkResponse(
        iterator = iterator,
        schema = SchemaDto(listOf(KolonnebeskrivelseDto("ID"))),
        innhold = innhold,
    )
}

class HistoriskImportRepoFake : HistoriskImportRepo {
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
            opprettet = fixedTidspunkt,
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

    override fun hentAlleImporter(): List<HistoriskImportOversikt> {
        return emptyList()
    }

    override fun slettImport(importId: UUID): SlettImportResultat {
        val eksisterende = import ?: return SlettImportResultat.IKKE_FUNNET
        if (eksisterende.id != importId) return SlettImportResultat.IKKE_FUNNET
        if (eksisterende.status == HistoriskImport.Status.PÅGÅR) return SlettImportResultat.PÅGÅR
        import = null
        return SlettImportResultat.SLETTET
    }
}

private class MultiImportRepoFake : HistoriskImportRepo {
    private val importer = mutableMapOf<UUID, HistoriskImport>()
    private val feilbeskrivelser = mutableMapOf<UUID, String>()

    override fun hentPågåendeImport(): HistoriskImport? =
        importer.values.firstOrNull { it.status == HistoriskImport.Status.PÅGÅR }

    override fun opprettImport(tabeller: List<NyHistoriskTabellimport>): HistoriskImport {
        val id = UUID.randomUUID()
        return HistoriskImport(
            id = id,
            status = HistoriskImport.Status.PÅGÅR,
            opprettet = fixedTidspunkt,
            tabeller = tabeller.map {
                HistoriskImport.Tabell(
                    tabellnavn = it.tabellnavn,
                    status = if (it.forventetAntall == 0L) HistoriskImport.Status.FULLFØRT else HistoriskImport.Status.PÅGÅR,
                    forventetAntall = it.forventetAntall,
                    importertAntall = 0,
                    nesteIterator = null,
                    nesteSide = 0,
                    kolonner = it.kolonner,
                )
            },
        ).also { importer[id] = it }
    }

    override fun lagreSide(side: HistoriskRådataSide): HistoriskImport.Tabell {
        val import = importer.getValue(side.importId)
        val eksisterende = import.tabeller.single { it.tabellnavn == side.tabellnavn }
        val oppdatert = eksisterende.copy(
            status = if (side.nesteIterator == null) HistoriskImport.Status.FULLFØRT else HistoriskImport.Status.PÅGÅR,
            importertAntall = eksisterende.importertAntall + side.rader.size,
            nesteIterator = side.nesteIterator,
            nesteSide = side.side + 1,
        )
        importer[side.importId] = import.copy(
            tabeller = import.tabeller.map { if (it.tabellnavn == side.tabellnavn) oppdatert else it },
        )
        return oppdatert
    }

    override fun fullførImport(importId: UUID) {
        importer[importId] = importer.getValue(importId).copy(status = HistoriskImport.Status.FULLFØRT)
    }

    override fun markerFeilet(importId: UUID, beskrivelse: String) {
        feilbeskrivelser[importId] = beskrivelse
        importer[importId] = importer.getValue(importId).copy(status = HistoriskImport.Status.FEILET)
    }

    override fun hentAlleImporter(): List<HistoriskImportOversikt> {
        return importer.values
            .filter { it.status != HistoriskImport.Status.PÅGÅR }
            .map { import ->
                HistoriskImportOversikt(
                    id = import.id,
                    status = import.status,
                    opprettet = import.opprettet,
                    fullført = if (import.status == HistoriskImport.Status.FULLFØRT) import.opprettet else null,
                    feilbeskrivelse = feilbeskrivelser[import.id],
                    tabeller = import.tabeller.map {
                        HistoriskImportTabellOversikt(
                            tabellnavn = it.tabellnavn,
                            status = it.status,
                            forventetAntall = it.forventetAntall,
                            importertAntall = it.importertAntall,
                        )
                    },
                )
            }
    }

    override fun slettImport(importId: UUID): SlettImportResultat {
        val eksisterende = importer[importId] ?: return SlettImportResultat.IKKE_FUNNET
        if (eksisterende.status == HistoriskImport.Status.PÅGÅR) return SlettImportResultat.PÅGÅR
        importer.remove(importId)
        return SlettImportResultat.SLETTET
    }
}
