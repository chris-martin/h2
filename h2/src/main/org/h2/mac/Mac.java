package org.h2.mac;

import org.h2.command.CommandInterface;
import org.h2.command.Parser;
import org.h2.command.ddl.AlterTableAddConstraint;
import org.h2.command.ddl.CreateIndex;
import org.h2.command.ddl.CreateTable;
import org.h2.command.ddl.CreateTableData;
import org.h2.command.dml.Select;
import org.h2.engine.Database;
import org.h2.engine.Session;
import org.h2.engine.User;
import org.h2.expression.*;
import org.h2.mac.SystemSessions.SystemTransaction;
import org.h2.mac.SystemSessions.SystemTransactionAction;
import org.h2.result.ResultInterface;
import org.h2.schema.Schema;
import org.h2.table.Column;
import org.h2.table.IndexColumn;
import org.h2.table.Table;
import org.h2.table.TableFilter;
import org.h2.util.New;
import org.h2.util.Utils;
import org.h2.value.Value;
import org.h2.value.ValueLong;
import org.h2.value.ValueString;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.h2.mac.Queries.*;
import static org.h2.mac.SystemSessions.executeSystemTransaction;
import static org.h2.message.DbException.throwInternalError;

public final class Mac {

    private Mac() { }

    public static void initializeMacSchema(Database database) {

        if (database.findSchema("MAC") == null) {

            executeSystemTransaction(database, new SystemTransactionAction<Void>() {

                @Override
                public Void execute(SystemTransaction transaction) {

                    new Parser(transaction.getSystemSession())
                        .prepareCommand(resource("/org/h2/mac/mac-init.sql"))
                        .update();

                    return null;
                }
            });
        }
    }

    private static String resource(String name) {
        try {
            return new String(Utils.getResource(name), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void grant(Session session, String markingString, final User grantee) {

        final Marking marking = Marking.parse(markingString);

        // todo check permissions

        executeSystemTransaction(session.getDatabase(), new SystemTransactionAction<Void>() {

            @Override
            public Void execute(SystemTransaction transaction) {

                grant(transaction, marking, grantee.getName());

                cleanupAllUserCredentials(transaction);

                return null;
            }
        });
    }

    private static void grant(SystemTransaction transaction, Marking marking, String grantee) {

        Session session = transaction.getSystemSession();

        if (marking.sensitivity == null) {
            throw throwInternalError("Granted credential must have a sensitivity");
        }

        Sensitivity sensitivity = marking.sensitivity;

        if (marking.compartments.size() != 1) {
            throw throwInternalError("Granted credential must have exactly one compartment");
        }

        Compartment compartment = marking.compartments.values().iterator().next();

        sensitivity.persist(transaction);
        compartment.persist(transaction);

        insert(session, lines(
            "insert into mac.user_credential ( user_name, credential_id )",
            "select",
            "  ? user_name,",
            "  mac.credential.credential_id credential_id",
            "from mac.credential",
            "where mac.credential.sensitivity_id = ?",
            "and mac.credential.compartment_id = ?"
        ), values(
            ValueString.get(grantee),
            ValueLong.get(sensitivity.id),
            ValueLong.get(compartment.id)
        ));
    }

    public static void revoke(Session session, String markingString, User grantee) {

        // todo check permissions

        // todo revoke access
    }

    public static Table createShadowTable(Database database, Schema shadowSchema, CreateTableData data) {

        data.id = 0;
        data = data.copy();
        data.schema = shadowSchema;

        boolean markingIdColumnAlreadyExists = false;
        for (Column column : data.columns) {
            if (column.getName().equalsIgnoreCase("MARKING_ID")) {
                markingIdColumnAlreadyExists = true;
            }
        }

        if (!markingIdColumnAlreadyExists) {
            Column markingColumn = new Column("MARKING_ID", Value.LONG);
            markingColumn.setDefaultExpression(data.session, ValueExpression.get(ValueLong.get(0)));
            data.columns.add(markingColumn);
        }

        new CreateTable(data).update();

        CreateIndex createIndex = new CreateIndex(data.session, shadowSchema);
        createIndex.setIndexName("INDEX_" + data.tableName + "_MARKING_ID");
        createIndex.setTableName(data.tableName);
        createIndex.setIndexColumns(new IndexColumn[]{IndexColumn.named("MARKING_ID")});
        createIndex.update();

        AlterTableAddConstraint fk = new AlterTableAddConstraint(data.session, shadowSchema, false);
        fk.setType(CommandInterface.ALTER_TABLE_ADD_CONSTRAINT_REFERENTIAL);
        fk.setTableName(data.tableName);
        fk.setIndexColumns(new IndexColumn[] { IndexColumn.named("MARKING_ID") });
        fk.setRefTableName(database.getSchema("MAC"), "MARKING");
        fk.setRefIndexColumns(new IndexColumn[] { IndexColumn.named("MARKING_ID") });
        fk.update();

        return shadowSchema.findTableOrView(data.session, data.tableName);
    }

    public static Select createViewQuery(Session session, Table shadowTable) {

        Schema shadowSchema = shadowTable.getSchema();
        Database database = shadowTable.getDatabase();

        Select select = new Select(session);

        ArrayList<Expression> expressions = New.arrayList();

        for (Column column : shadowTable.getColumns()) {
            String columnName = column.getName();
            if (!columnName.equalsIgnoreCase("MARKING_ID")) {
                expressions.add(
                    new Alias(
                        new ExpressionColumn(database, shadowSchema.getName(), shadowTable.getName(), columnName),
                        columnName,
                        false
                    )
                );
            }
        }

        Function markingFunction = Function.getFunction(database, "RENDER_MARKING");
        markingFunction.setParameter(0,
            new ExpressionColumn(database, shadowSchema.getName(), shadowTable.getName(), "MARKING_ID"));
        expressions.add(new Alias(markingFunction, "MARKING", false));

        select.setExpressions(expressions);

        TableFilter shadowFilter = new TableFilter(session, shadowTable, null, true, select);
        select.addTableFilter(shadowFilter, true);

        Table sessionMarkingTable = database.getSchema("MAC").getTableOrView(session, "SESSION_MARKING");
        TableFilter sessionMarkingFilter = new TableFilter(session, sessionMarkingTable, null, true, select);
        Expression joinExpression = new Comparison(session, Comparison.EQUAL,
            new ExpressionColumn(database, shadowSchema.getName(), shadowTable.getName(), "MARKING_ID"),
            new ExpressionColumn(database, "MAC", sessionMarkingTable.getName(), "MARKING_ID")
        );
        shadowFilter.addJoin(sessionMarkingFilter, false, false, joinExpression);
        select.init();
        return select;
    }

    public static void cleanupAllUserCredentials(SystemTransaction transaction) {

        Session session = transaction.getSystemSession();

        // todo this could probably be done more efficiently in a single query

        List<Sensitivity> sensitivities = selectList(session, lines(
            "select sensitivity_id, name from mac.sensitivity where sensitivity_id <> 0"
        ), values(), new RowMapper<Sensitivity>() {
            @Override
            public Sensitivity apply(ResultInterface result) {
                Value[] values = result.currentRow();
                return new Sensitivity(values[0].getLong(), values[1].getString());
            }
        });

        List<String> users = selectList(session, lines(
            "select distinct user_name from mac.user_credential"
        ), values(), new RowMapper<String>() {
            @Override
            public String apply(ResultInterface result) {
                Value values = result.currentRow()[0];
                return values.getString();
            }
        });

        for (String user : users) {

            List<Long> compartmentIds = selectList(session, lines(
                "select distinct mac.credential.compartment_id",
                "from mac.credential",
                "join mac.user_credential",
                "on mac.credential.credential_id = mac.user_credential.credential_id",
                "where mac.user_credential.user_name = ?"
            ), values(
                ValueString.get(user)
            ), new RowMapper<Long>() {
                @Override
                public Long apply(ResultInterface result) {
                    Value[] values = result.currentRow();
                    return values[0].getLong();
                }
            });

            for (Long compartmentId : compartmentIds) {

                List<Sensitivity> grantedSensitivities = selectList(session, lines(
                    "select",
                    "  mac.sensitivity.sensitivity_id,",
                    "  mac.sensitivity.name",
                    "from mac.user_credential",
                    "join mac.credential",
                    "on mac.credential.credential_id = mac.user_credential.credential_id",
                    "join mac.sensitivity",
                    "on mac.sensitivity.sensitivity_id = mac.credential.sensitivity_id",
                    "where mac.user_credential.user_name = ?",
                    "and mac.credential.compartment_id = ?"
                ), values(
                    ValueString.get(user),
                    ValueLong.get(compartmentId)
                ), new RowMapper<Sensitivity>() {
                    @Override
                    public Sensitivity apply(ResultInterface result) {
                        Value[] values = result.currentRow();
                        return new Sensitivity(
                            values[0].getLong(),
                            values[1].getString()
                        );
                    }
                });

                for (final Sensitivity sensitivity : sensitivities) {

                    boolean alreadyGranted = contains(grantedSensitivities, new Predicate<Sensitivity>() {
                        @Override
                        public boolean apply(Sensitivity x) {
                            return x.id.equals(sensitivity.id);
                        }
                    });

                    boolean grantedHigher = contains(grantedSensitivities, new Predicate<Sensitivity>() {
                        @Override
                        public boolean apply(Sensitivity x) {
                            return x.name.compareTo(sensitivity.name) > 0;
                        }
                    });

                    if (!alreadyGranted && grantedHigher) {
                        grant(transaction, new Marking(sensitivity, new Compartment(compartmentId)), user);
                    }
                }
            }
        }
    }

    private static interface Predicate<T> {
        boolean apply(T t);
    }

    private static <T> boolean contains(Collection<T> collection, Predicate<T> predicate) {

        for (T t : collection) {
            if (predicate.apply(t)) {
                return true;
            }
        }

        return false;
    }
}
