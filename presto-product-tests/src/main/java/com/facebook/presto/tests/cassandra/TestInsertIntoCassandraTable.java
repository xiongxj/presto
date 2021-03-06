/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.tests.cassandra;

import com.teradata.tempto.ProductTest;
import com.teradata.tempto.Requirement;
import com.teradata.tempto.RequirementsProvider;
import com.teradata.tempto.configuration.Configuration;
import com.teradata.tempto.internal.fulfillment.table.TableName;
import com.teradata.tempto.query.QueryResult;
import io.airlift.units.Duration;
import org.testng.annotations.Test;

import static com.facebook.presto.tests.TemptoProductTestRunner.PRODUCT_TESTS_TIME_ZONE;
import static com.facebook.presto.tests.TestGroups.CASSANDRA;
import static com.facebook.presto.tests.cassandra.DataTypesTableDefinition.CASSANDRA_ALL_TYPES;
import static com.facebook.presto.tests.cassandra.TestConstants.CONNECTOR_NAME;
import static com.facebook.presto.tests.cassandra.TestConstants.KEY_SPACE;
import static com.facebook.presto.tests.utils.QueryAssertions.assertContainsEventually;
import static com.teradata.tempto.assertions.QueryAssert.Row.row;
import static com.teradata.tempto.assertions.QueryAssert.assertThat;
import static com.teradata.tempto.fulfillment.table.MutableTableRequirement.State.CREATED;
import static com.teradata.tempto.fulfillment.table.MutableTablesState.mutableTablesState;
import static com.teradata.tempto.fulfillment.table.TableRequirements.mutableTable;
import static com.teradata.tempto.query.QueryExecutor.query;
import static com.teradata.tempto.util.DateTimeUtils.parseTimestampInLocalTime;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MINUTES;

public class TestInsertIntoCassandraTable
        extends ProductTest
        implements RequirementsProvider
{
    private static final String CASSANDRA_INSERT_TABLE = "Insert_All_Types";

    @Override
    public Requirement getRequirements(Configuration configuration)
    {
        return mutableTable(CASSANDRA_ALL_TYPES, CASSANDRA_INSERT_TABLE, CREATED);
    }

    @Test(groups = CASSANDRA)
    public void testInsertIntoValuesToCassandraTableAllSimpleTypes()
            throws Exception
    {
        TableName table = mutableTablesState().get(CASSANDRA_INSERT_TABLE).getTableName();
        String tableNameInDatabase = String.format("%s.%s", CONNECTOR_NAME, table.getNameInDatabase());

        assertContainsEventually(() -> query(format("SHOW TABLES FROM %s.%s", CONNECTOR_NAME, KEY_SPACE)),
                query(format("SELECT '%s'", table.getSchemalessNameInDatabase())),
                new Duration(1, MINUTES));

        QueryResult queryResult = query("SELECT * FROM " + tableNameInDatabase);
        assertThat(queryResult).hasNoRows();

        // TODO Following types are not supported now. We need to change null into the value after fixing it
        // blob, frozen<set<type>>, inet, list<type>, map<type,type>, set<type>, timeuuid, decimal, uuid, varint
        query("INSERT INTO " + tableNameInDatabase +
                "(a, b, bl, bo, d, do, f, fr, i, integer, l, m, s, t, ti, tu, u, v, vari) VALUES (" +
                "'ascii value', " +
                "BIGINT '99999', " +
                "null, " +
                "true, " +
                "null, " +
                "123.456789, " +
                "REAL '123.45678', " +
                "null, " +
                "null, " +
                "123, " +
                "null, " +
                "null, " +
                "null, " +
                "'text value', " +
                "timestamp '9999-12-31 23:59:59'," +
                "null, " +
                "null, " +
                "'varchar value'," +
                "null)");

        assertThat(query("SELECT * FROM " + tableNameInDatabase)).containsOnly(
                row(
                        "ascii value",
                        99999,
                        null,
                        true,
                        null,
                        123.456789,
                        123.45678,
                        null,
                        null,
                        123,
                        null,
                        null,
                        null,
                        "text value",
                        parseTimestampInLocalTime("9999-12-31 23:59:59", PRODUCT_TESTS_TIME_ZONE),
                        null,
                        null,
                        "varchar value",
                        null));

        // insert null for all datatypes
        query("INSERT INTO " + tableNameInDatabase +
                "(a, b, bl, bo, d, do, f, fr, i, integer, l, m, s, t, ti, tu, u, v, vari) VALUES (" +
                "'key 1', null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null) ");
        assertThat(query(format("SELECT * FROM %s WHERE a = 'key 1'", tableNameInDatabase))).containsOnly(
                row("key 1", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));

        // insert into only a subset of columns
        query(format("INSERT INTO %s (a, bo, integer, t) VALUES ('key 2', false, 999, 'text 2')", tableNameInDatabase));
        assertThat(query(format("SELECT * FROM %s WHERE a = 'key 2'", tableNameInDatabase))).containsOnly(
                row("key 2", null, null, false, null, null, null, null, null, 999, null, null, null, "text 2", null, null, null, null, null));

        // negative test: failed to insert null to primary key
        assertThat(() -> query(format("INSERT INTO %s (a) VALUES (null) ", tableNameInDatabase)))
                .failsWithMessage("Invalid null value in condition for column a");
    }
}
