package org.h2.schema;

import org.h2.command.CommandInterface;
import org.h2.command.ddl.AlterTableAddConstraint;
import org.h2.command.ddl.CreateTable;
import org.h2.command.ddl.CreateTableData;
import org.h2.command.ddl.CreateView;
import org.h2.command.dml.Select;
import org.h2.constant.SysProperties;
import org.h2.constraint.Constraint;
import org.h2.engine.*;
import org.h2.expression.*;
import org.h2.index.Index;
import org.h2.message.Trace;
import org.h2.table.*;
import org.h2.util.New;
import org.h2.value.Value;
import org.h2.value.ValueLong;

import java.util.ArrayList;
import java.util.HashMap;

import static java.util.Arrays.asList;
import static org.h2.message.DbException.throwInternalError;

/**
 * A schema as created by the SQL statement CREATE SCHEMA RESTRICTED.
 *
 * This schema contains only views, referencing the MAC schema and the shadow schema.
 */
public class RestrictedSchema extends SchemaBase {

    private final RegularSchema shadowSchema;

    private final HashMap<String, TableView> views;

    public RestrictedSchema(RegularSchema shadowSchema, int id, String schemaName) {
        this.shadowSchema = shadowSchema;
        Database database = shadowSchema.getDatabase();
        views = database.newStringMap();
        initDbObjectBase(database, id, schemaName, Trace.SCHEMA);
    }

    @Override
    public boolean isRestricted() {
        return true;
    }

    @Override
    public RestrictedSchema asRestricted() {
        return this;
    }

    public Table getShadowTable(TableView view) {

        return shadowSchema.getTableOrView(null, view.getName());
    }

    @Override
    public String getCreateSQL() {
        return "CREATE SCHEMA RESTRICTED IF NOT EXISTS " +
            getSQL() + " AUTHORIZATION " + getOwner().getSQL();
    }

    @Override
    public String getDropSQL() {
        return null;
    }

    @Override
    public void removeChildrenAndResources(Session session) {
        removeTablesAndViews(session, views);
        shadowSchema.removeChildrenAndResources(session);
    }

    @Override
    public void checkRename() {
        shadowSchema.checkRename();
    }

    @Override
    public String getCreateSQLForCopy(Table table, String quotedName) {
        return shadowSchema.getCreateSQLForCopy(table, quotedName);
    }

    @Override
    public int getType() {
        return shadowSchema.getType();
    }

    @Override
    public boolean canDrop() {
        return true;
    }

    @Override
    public User getOwner() {
        return shadowSchema.getOwner();
    }

    @Override
    public void add(SchemaObject obj) {
        if (obj.getType() != DbObject.TABLE_OR_VIEW) {
            shadowSchema.add(obj);
            return;
        }
        String name = obj.getName();
        if (SysProperties.CHECK && views.get(name) != null) {
            throw throwInternalError("object already exists: " + name);
        }
        views.put(name, (TableView) obj);
        freeUniqueName(name);
    }

    @Override
    public void rename(SchemaObject obj, String newName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Table findTableOrView(Session session, String name) {
        Table table = views.get(name);
        if (table == null && session != null) {
            table = session.findLocalTempTable(name);
        }
        return table;
    }

    @Override
    public Index findIndex(Session session, String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TriggerObject findTrigger(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Sequence findSequence(String sequenceName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Constraint findConstraint(Session session, String name) {
        return shadowSchema.findConstraint(session, name);
    }

    @Override
    public Constant findConstant(String constantName) {
        return shadowSchema.findConstant(constantName);
    }

    @Override
    public FunctionAlias findFunction(String functionAlias) {
        return shadowSchema.findFunction(functionAlias);
    }

    @Override
    public void freeUniqueName(String name) {
        shadowSchema.freeUniqueName(name);
    }

    @Override
    public String getUniqueConstraintName(Session session, Table table) {
        return shadowSchema.getUniqueConstraintName(session, table);
    }

    @Override
    public String getUniqueIndexName(Session session, Table table, String prefix) {
        return shadowSchema.getUniqueIndexName(session, table, prefix);
    }

    @Override
    public Table getTableOrView(Session session, String name) {

        TableView view = views.get(name);

        if (view == null) {
            throw throwInternalError("View not found in restricted schema: " + name);
        }

        return view;
    }

    @Override
    public Index getIndex(String name) {
        return shadowSchema.getIndex(name);
    }

    @Override
    public Constraint getConstraint(String name) {
        return shadowSchema.getConstraint(name);
    }

    @Override
    public Constant getConstant(String constantName) {
        return shadowSchema.getConstant(constantName);
    }

    @Override
    public Sequence getSequence(String sequenceName) {
        return shadowSchema.getSequence(sequenceName);
    }

    @Override
    public ArrayList<SchemaObject> getAll() {
        ArrayList<SchemaObject> all = New.arrayList();

        all.addAll(views.values());

        all.addAll(shadowSchema.getMap(DbObject.SEQUENCE).values());
        all.addAll(shadowSchema.getMap(DbObject.INDEX).values());
        all.addAll(shadowSchema.getMap(DbObject.TRIGGER).values());
        all.addAll(shadowSchema.getMap(DbObject.CONSTRAINT).values());
        all.addAll(shadowSchema.getMap(DbObject.CONSTANT).values());
        all.addAll(shadowSchema.getMap(DbObject.FUNCTION_ALIAS).values());
        return all;
    }

    @Override
    public ArrayList<SchemaObject> getAll(int type) {

        HashMap<String, ? extends SchemaObject> map =
            type == DbObject.TABLE_OR_VIEW
                ? views
                : shadowSchema.getMap(type);

        return New.arrayList(map.values());
    }

    @Override
    public ArrayList<Table> getAllTablesAndViews() {
        synchronized (database) {
            return New.<Table>arrayList(views.values());
        }
    }

    @Override
    public void remove(SchemaObject obj) {
        shadowSchema.remove(obj);
    }

    @Override
    public Table createTable(CreateTableData data) {

        Table shadowTable = createShadowTable(data);

        CreateView createView = new CreateView(data.session, this);
        createView.setSelect(createViewQuery(data.session, shadowTable));
        createView.setViewName(data.tableName);
        createView.setColumnNames(getColumnNames(shadowTable.getColumns()));
        createView.update();

        return null;
    }

    private static String[] getColumnNames(Column[] columns) {
        String[] names = new String[columns.length];
        for (int i = 0; i < columns.length; i++) {
            names[i] = columns[i].getName();
        }
        return names;
    }

    private Table createShadowTable(CreateTableData data) {

        data.id = 0;
        data = data.copy();
        data.schema = shadowSchema;
        Column markingColumn = new Column("MARKING_ID", Value.LONG);
        markingColumn.setDefaultExpression(data.session, ValueExpression.get(ValueLong.get(0)));
        data.columns.add(markingColumn);
        new CreateTable(data).update();

        AlterTableAddConstraint fk = new AlterTableAddConstraint(data.session, shadowSchema, false);
        fk.setType(CommandInterface.ALTER_TABLE_ADD_CONSTRAINT_REFERENTIAL);
        fk.setTableName(data.tableName);
        fk.setIndexColumns(new IndexColumn[] { IndexColumn.named("MARKING_ID") });
        fk.setRefTableName(database.getSchema("MAC"), "MARKING");
        fk.setRefIndexColumns(new IndexColumn[] { IndexColumn.named("MARKING_ID") });
        fk.update();

        return shadowSchema.findTableOrView(data.session, data.tableName);
    }

    private Select createViewQuery(Session session, Table shadowTable) {

        Select select = new Select(session);

        // todo select everything from shadowTable except MARKING_ID
        select.setExpressions(New.<Expression>arrayList(asList(new Wildcard(null, null))));

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

    @Override
    public TableLink createTableLink(
        int id, String tableName, String driver, String url, String user, String password,
        String originalSchema, String originalTable, boolean emitUpdates, boolean force
    ) {
        throw new UnsupportedOperationException();
    }
}
