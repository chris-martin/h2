package org.h2.schema;

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
import org.h2.table.Table;
import org.h2.table.TableFilter;
import org.h2.table.TableLink;
import org.h2.table.TableView;
import org.h2.util.New;
import org.h2.value.ValueInt;

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

    public Table getShadowTable(Session session, TableView view) {

        return shadowSchema.getTableOrView(session, view.getName());
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
            throw throwInternalError("restricted schema may only contain views");
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
        throw new UnsupportedOperationException();
    }

    @Override
    public Constant findConstant(String constantName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FunctionAlias findFunction(String functionAlias) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void freeUniqueName(String name) {
    }

    @Override
    public String getUniqueConstraintName(Session session, Table table) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getUniqueIndexName(Session session, Table table, String prefix) {
        throw new UnsupportedOperationException();
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
        throw new UnsupportedOperationException();
    }

    @Override
    public Constraint getConstraint(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Constant getConstant(String constantName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Sequence getSequence(String sequenceName) {
        throw new UnsupportedOperationException();
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
        throw new UnsupportedOperationException();
    }

    @Override
    public Table createTable(CreateTableData data) {

        data.id = 0;
        data = data.copy();
        data.schema = shadowSchema;
        new CreateTable(data).update();

        Table table = shadowSchema.findTableOrView(data.session, data.tableName);

        CreateView createView = new CreateView(data.session, this);

        Select select = new Select(data.session);
        select.setExpressions(New.<Expression>arrayList(asList(new Wildcard(null, null))));
        select.addTableFilter(new TableFilter(data.session, table, null, true, select), true);
        select.addCondition(new Comparison(
            data.session,
            Comparison.EQUAL,
            new ExpressionColumn(database, "VAULT_shadow", "DOC", "X"), // todo
            ValueExpression.get(ValueInt.get(0))
        ));
        select.init();
        createView.setSelect(select);

        createView.setViewName(data.tableName);

        String[] columnNames = new String[table.getColumns().length];
        for (int i = 0; i < columnNames.length; i++) {
            columnNames[i] = table.getColumns()[i].getName();
        }
        createView.setColumnNames(columnNames);

        createView.update();

        return null;
    }

    @Override
    public TableLink createTableLink(
        int id, String tableName, String driver, String url, String user, String password,
        String originalSchema, String originalTable, boolean emitUpdates, boolean force
    ) {
        throw new UnsupportedOperationException();
    }
}
