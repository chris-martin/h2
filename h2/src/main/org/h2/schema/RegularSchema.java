/*
 * Copyright 2004-2013 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.schema;

import org.h2.api.TableEngine;
import org.h2.command.ddl.CreateTableData;
import org.h2.constant.ErrorCode;
import org.h2.constant.SysProperties;
import org.h2.constraint.Constraint;
import org.h2.engine.*;
import org.h2.index.Index;
import org.h2.message.DbException;
import org.h2.message.Trace;
import org.h2.mvstore.db.MVTableEngine;
import org.h2.table.RegularTable;
import org.h2.table.Table;
import org.h2.table.TableLink;
import org.h2.util.New;
import org.h2.util.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static org.h2.message.DbException.throwInternalError;

/**
 * A schema as created by the SQL statement
 * CREATE SCHEMA
 */
public class RegularSchema extends SchemaBase {

    private User owner;
    private final boolean system;
    private final boolean shadow;

    private final HashMap<String, Table> tablesAndViews;
    private final HashMap<String, Index> indexes;
    private final HashMap<String, Sequence> sequences;
    private final HashMap<String, TriggerObject> triggers;
    private final HashMap<String, Constraint> constraints;
    private final HashMap<String, Constant> constants;
    private final HashMap<String, FunctionAlias> functions;

    /**
     * The set of returned unique names that are not yet stored. It is used to
     * avoid returning the same unique name twice when multiple threads
     * concurrently create objects.
     */
    private final HashSet<String> temporaryUniqueNames = New.hashSet();

    /**
     * Create a new schema object.
     *
     * @param database the database
     * @param id the object id
     * @param schemaName the schema name
     * @param owner the owner of the schema
     * @param system if this is a system schema (such a schema can not be
     *            dropped)
     */
    public RegularSchema(Database database, int id, String schemaName, User owner, boolean system, boolean shadow) {
        tablesAndViews = database.newStringMap();
        indexes = database.newStringMap();
        sequences = database.newStringMap();
        triggers = database.newStringMap();
        constraints = database.newStringMap();
        constants = database.newStringMap();
        functions = database.newStringMap();
        initDbObjectBase(database, id, schemaName, Trace.SCHEMA);
        this.owner = owner;
        this.system = system;
        this.shadow = shadow;
    }

    @Override
    public boolean isRestricted() {
        return false;
    }

    @Override
    public RestrictedSchema asRestricted() {
        throw throwInternalError("not a restricted schema: " + getName());
    }

    @Override
    public boolean canDrop() {
        return !system;
    }

    @Override
    public String getCreateSQLForCopy(Table table, String quotedName) {
        throw throwInternalError();
    }

    @Override
    public String getDropSQL() {
        return null;
    }

    @Override
    public String getCreateSQL() {
        if (system) {
            return null;
        }
        return "CREATE SCHEMA IF NOT EXISTS " +
            getSQL() + " AUTHORIZATION " + owner.getSQL();
    }

    @Override
    public int getType() {
        return DbObject.SCHEMA;
    }

    @Override
    public void removeChildrenAndResources(Session session) {
        while (triggers != null && triggers.size() > 0) {
            TriggerObject obj = (TriggerObject) triggers.values().toArray()[0];
            database.removeSchemaObject(session, obj);
        }
        while (constraints != null && constraints.size() > 0) {
            Constraint obj = (Constraint) constraints.values().toArray()[0];
            database.removeSchemaObject(session, obj);
        }
        while (constraints != null && constraints.size() > 0) {
            Constraint obj = (Constraint) constraints.values().toArray()[0];
            database.removeSchemaObject(session, obj);
        }

        removeTablesAndViews(session, tablesAndViews);

        while (indexes != null && indexes.size() > 0) {
            Index obj = (Index) indexes.values().toArray()[0];
            database.removeSchemaObject(session, obj);
        }
        while (sequences != null && sequences.size() > 0) {
            Sequence obj = (Sequence) sequences.values().toArray()[0];
            database.removeSchemaObject(session, obj);
        }
        while (constants != null && constants.size() > 0) {
            Constant obj = (Constant) constants.values().toArray()[0];
            database.removeSchemaObject(session, obj);
        }
        while (functions != null && functions.size() > 0) {
            FunctionAlias obj = (FunctionAlias) functions.values().toArray()[0];
            database.removeSchemaObject(session, obj);
        }
        database.removeMeta(session, getId());
        owner = null;
        invalidate();
    }

    @Override
    public void checkRename() {
        // ok
    }

    @Override
    public User getOwner() {
        return owner;
    }

    @SuppressWarnings("unchecked")
    HashMap<String, SchemaObject> getMap(int type) {
        HashMap<String, ? extends SchemaObject> result;
        switch (type) {
        case DbObject.TABLE_OR_VIEW:
            result = tablesAndViews;
            break;
        case DbObject.SEQUENCE:
            result = sequences;
            break;
        case DbObject.INDEX:
            result = indexes;
            break;
        case DbObject.TRIGGER:
            result = triggers;
            break;
        case DbObject.CONSTRAINT:
            result = constraints;
            break;
        case DbObject.CONSTANT:
            result = constants;
            break;
        case DbObject.FUNCTION_ALIAS:
            result = functions;
            break;
        default:
            throw throwInternalError("type=" + type);
        }
        return (HashMap) result;
    }

    @Override
    public void add(SchemaObject obj) {
        if (SysProperties.CHECK && obj.getSchema() != this) {
            throwInternalError("wrong schema");
        }
        String name = obj.getName();
        HashMap<String, SchemaObject> map = getMap(obj.getType());
        if (SysProperties.CHECK && map.get(name) != null) {
            throwInternalError("object already exists: " + name);
        }
        map.put(name, obj);
        freeUniqueName(name);
    }

    @Override
    public void rename(SchemaObject obj, String newName) {
        int type = obj.getType();
        HashMap<String, SchemaObject> map = getMap(type);
        if (SysProperties.CHECK) {
            if (!map.containsKey(obj.getName())) {
                throwInternalError("not found: " + obj.getName());
            }
            if (obj.getName().equals(newName) || map.containsKey(newName)) {
                throwInternalError("object already exists: " + newName);
            }
        }
        obj.checkRename();
        map.remove(obj.getName());
        freeUniqueName(obj.getName());
        obj.rename(newName);
        map.put(newName, obj);
        freeUniqueName(newName);
    }

    @Override
    public Table findTableOrView(Session session, String name) {
        Table table = tablesAndViews.get(name);
        if (table == null && session != null) {
            table = session.findLocalTempTable(name);
        }
        return table;
    }

    @Override
    public Index findIndex(Session session, String name) {
        Index index = indexes.get(name);
        if (index == null) {
            index = session.findLocalTempTableIndex(name);
        }
        return index;
    }

    @Override
    public TriggerObject findTrigger(String name) {
        return triggers.get(name);
    }

    @Override
    public Sequence findSequence(String sequenceName) {
        return sequences.get(sequenceName);
    }

    @Override
    public Constraint findConstraint(Session session, String name) {
        Constraint constraint = constraints.get(name);
        if (constraint == null) {
            constraint = session.findLocalTempTableConstraint(name);
        }
        return constraint;
    }

    @Override
    public Constant findConstant(String constantName) {
        return constants.get(constantName);
    }

    @Override
    public FunctionAlias findFunction(String functionAlias) {
        return functions.get(functionAlias);
    }

    @Override
    public void freeUniqueName(String name) {
        if (name != null) {
            synchronized (temporaryUniqueNames) {
                temporaryUniqueNames.remove(name);
            }
        }
    }

    private String getUniqueName(DbObject obj, HashMap<String, ? extends SchemaObject> map, String prefix) {
        String hash = Integer.toHexString(obj.getName().hashCode()).toUpperCase();
        String name = null;
        synchronized (temporaryUniqueNames) {
            for (int i = 1, len = hash.length(); i < len; i++) {
                name = prefix + hash.substring(0, i);
                if (!map.containsKey(name) && !temporaryUniqueNames.contains(name)) {
                    break;
                }
                name = null;
            }
            if (name == null) {
                prefix = prefix + hash + "_";
                for (int i = 0;; i++) {
                    name = prefix + i;
                    if (!map.containsKey(name) && !temporaryUniqueNames.contains(name)) {
                        break;
                    }
                }
            }
            temporaryUniqueNames.add(name);
        }
        return name;
    }

    @Override
    public String getUniqueConstraintName(Session session, Table table) {
        HashMap<String, Constraint> tableConstraints;
        if (table.isTemporary() && !table.isGlobalTemporary()) {
            tableConstraints = session.getLocalTempTableConstraints();
        } else {
            tableConstraints = constraints;
        }
        return getUniqueName(table, tableConstraints, "CONSTRAINT_");
    }

    @Override
    public String getUniqueIndexName(Session session, Table table, String prefix) {
        HashMap<String, Index> tableIndexes;
        if (table.isTemporary() && !table.isGlobalTemporary()) {
            tableIndexes = session.getLocalTempTableIndexes();
        } else {
            tableIndexes = indexes;
        }
        return getUniqueName(table, tableIndexes, prefix);
    }

    @Override
    public Table getTableOrView(Session session, String name) {
        Table table = tablesAndViews.get(name);
        if (table == null) {
            if (session != null) {
                table = session.findLocalTempTable(name);
            }
            if (table == null) {
                throw DbException.get(ErrorCode.TABLE_OR_VIEW_NOT_FOUND_1, name);
            }
        }
        return table;
    }

    @Override
    public Index getIndex(String name) {
        Index index = indexes.get(name);
        if (index == null) {
            throw DbException.get(ErrorCode.INDEX_NOT_FOUND_1, name);
        }
        return index;
    }

    @Override
    public Constraint getConstraint(String name) {
        Constraint constraint = constraints.get(name);
        if (constraint == null) {
            throw DbException.get(ErrorCode.CONSTRAINT_NOT_FOUND_1, name);
        }
        return constraint;
    }

    @Override
    public Constant getConstant(String constantName) {
        Constant constant = constants.get(constantName);
        if (constant == null) {
            throw DbException.get(ErrorCode.CONSTANT_NOT_FOUND_1, constantName);
        }
        return constant;
    }

    @Override
    public Sequence getSequence(String sequenceName) {
        Sequence sequence = sequences.get(sequenceName);
        if (sequence == null) {
            throw DbException.get(ErrorCode.SEQUENCE_NOT_FOUND_1, sequenceName);
        }
        return sequence;
    }

    @Override
    public ArrayList<SchemaObject> getAll() {
        ArrayList<SchemaObject> all = New.arrayList();
        all.addAll(getMap(DbObject.TABLE_OR_VIEW).values());
        all.addAll(getMap(DbObject.SEQUENCE).values());
        all.addAll(getMap(DbObject.INDEX).values());
        all.addAll(getMap(DbObject.TRIGGER).values());
        all.addAll(getMap(DbObject.CONSTRAINT).values());
        all.addAll(getMap(DbObject.CONSTANT).values());
        all.addAll(getMap(DbObject.FUNCTION_ALIAS).values());
        return all;
    }

    @Override
    public ArrayList<SchemaObject> getAll(int type) {
        HashMap<String, SchemaObject> map = getMap(type);
        return New.arrayList(map.values());
    }

    @Override
    public ArrayList<Table> getAllTablesAndViews() {
        synchronized (database) {
            return New.arrayList(tablesAndViews.values());
        }
    }

    @Override
    public void remove(SchemaObject obj) {
        String objName = obj.getName();
        HashMap<String, SchemaObject> map = getMap(obj.getType());
        if (SysProperties.CHECK && !map.containsKey(objName)) {
            throwInternalError("not found: " + objName);
        }
        map.remove(objName);
        freeUniqueName(objName);
    }

    @Override
    public Table createTable(CreateTableData data) {
        synchronized (database) {
            if (!data.temporary || data.globalTemporary) {
                database.lockMeta(data.session);
            }
            data.schema = this;
            if (data.tableEngine == null) {
                if (database.getSettings().mvStore) {
                    data.tableEngine = MVTableEngine.class.getName();
                }
            }
            if (data.tableEngine != null) {
                TableEngine engine;
                try {
                    engine = (TableEngine) Utils.loadUserClass(data.tableEngine).newInstance();
                } catch (Exception e) {
                    throw DbException.convert(e);
                }
                return engine.createTable(data);
            }
            return new RegularTable(data);
        }
    }

    @Override
    public TableLink createTableLink(int id, String tableName,
            String driver, String url, String user, String password,
            String originalSchema, String originalTable, boolean emitUpdates, boolean force) {
        synchronized (database) {
            return new TableLink(this, id, tableName,
                    driver, url, user, password,
                    originalSchema, originalTable, emitUpdates, force);
        }
    }

}
