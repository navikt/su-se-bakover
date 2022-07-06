package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.util.UUID

internal class V47__add_tilhørighet_to_fradrag : BaseJavaMigration() {
    override fun migrate(context: Context?) {
        val statement = context!!.connection.createStatement()
        val rs = statement.executeQuery("select id, beregning from behandling")
        val beregninger: MutableMap<String, String> = mutableMapOf()

        while (rs.next()) {
            rs.getString("beregning")?.let {
                beregninger[rs.getString("id")] = it
            }
        }
        rs.close()

        val migrated = AddTilhørerToFradrag.migrate(beregninger)

        val ps = context.connection.prepareStatement("update behandling set beregning = to_json(?::json) where id = ?")

        migrated.forEach {
            ps.setString(1, it.value)
            ps.setObject(2, UUID.fromString(it.key))
            ps.addBatch()
        }
        ps.executeBatch()
        ps.close()
        statement.close()
    }
}

internal object AddTilhørerToFradrag {
    fun migrate(beregninger: Map<String, String>): Map<String, String> {
        val beregningerJson = beregninger.map { it.key to objectMapper.readTree(it.value) }.toMap()
        beregningerJson.values.forEach { beregning ->
            beregning.path("fradrag").forEach {
                (it as ObjectNode).put("tilhører", FradragTilhører.BRUKER.name)
            }
            beregning.path("månedsberegninger").forEach { månedsberegning ->
                månedsberegning.path("fradrag").forEach {
                    (it as ObjectNode).put("tilhører", FradragTilhører.BRUKER.name)
                }
            }
        }
        return beregningerJson.map { it.key to serialize(it.value) }.toMap()
    }
}
