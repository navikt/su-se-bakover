package db.migration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

public class V42__store_beregning_as_json_and_patch_existing extends BaseJavaMigration {
    private ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .addModule(new KotlinModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
            .build();

    @Override
    public void migrate(Context context) throws Exception {
        Statement statement = context.getConnection().createStatement();
        statement.execute("alter table behandling add column if not exists beregning jsonb");
        /**
         * Deleted code was here (see git history).
         * Code contained migration of existing db-data, but was removed as it was no longer compiling.
         */
        statement.close();
    }
}
