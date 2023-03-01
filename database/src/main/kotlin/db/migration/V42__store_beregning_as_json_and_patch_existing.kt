package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

class V42__store_beregning_as_json_and_patch_existing : BaseJavaMigration() {
    @Throws(Exception::class)
    override fun migrate(context: Context) {
        val statement = context.connection.createStatement()
        statement.execute("alter table behandling add column if not exists beregning jsonb")
        /**
         * Deleted code was here (see git history).
         * Code contained migration of existing db-data, but was removed as it was no longer compiling.
         */
        statement.close()
    }
}
