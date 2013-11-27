package org.h2.schema;

import org.h2.command.ddl.CreateTableData;
import org.h2.constraint.Constraint;
import org.h2.engine.DbObject;
import org.h2.engine.FunctionAlias;
import org.h2.engine.Session;
import org.h2.engine.User;
import org.h2.index.Index;
import org.h2.message.DbException;
import org.h2.table.Table;
import org.h2.table.TableLink;

import java.util.ArrayList;

public interface Schema extends DbObject {

    boolean isRestricted();

    RestrictedSchema asRestricted();

    /**
     * Tell the object that is was modified.
     */
    void setModified();

    long getModificationId();

    /**
     * Check if this schema can be dropped. System schemas can not be dropped.
     *
     * @return true if it can be dropped
     */
    boolean canDrop();

    /**
     * Get the owner of this schema.
     *
     * @return the owner
     */
    User getOwner();

    /**
     * Add an object to this schema.
     * This method must not be called within CreateSchemaObject;
     * use Database.addSchemaObject() instead
     *
     * @param obj the object to add
     */
    void add(SchemaObject obj);

    /**
     * Rename an object.
     *
     * @param obj the object to rename
     * @param newName the new name
     */
    void rename(SchemaObject obj, String newName);

    /**
     * Try to find a table or view with this name. This method returns null if
     * no object with this name exists. Local temporary tables are also
     * returned.
     *
     * @param session the session
     * @param name the object name
     * @return the object or null
     */
    Table findTableOrView(Session session, String name);

    /**
     * Try to find an index with this name. This method returns null if
     * no object with this name exists.
     *
     * @param session the session
     * @param name the object name
     * @return the object or null
     */
    Index findIndex(Session session, String name);

    /**
     * Try to find a trigger with this name. This method returns null if
     * no object with this name exists.
     *
     * @param name the object name
     * @return the object or null
     */
    TriggerObject findTrigger(String name);

    /**
     * Try to find a sequence with this name. This method returns null if
     * no object with this name exists.
     *
     * @param sequenceName the object name
     * @return the object or null
     */
    Sequence findSequence(String sequenceName);

    /**
     * Try to find a constraint with this name. This method returns null if no
     * object with this name exists.
     *
     * @param session the session
     * @param name the object name
     * @return the object or null
     */
    Constraint findConstraint(Session session, String name);

    /**
     * Try to find a user defined constant with this name. This method returns
     * null if no object with this name exists.
     *
     * @param constantName the object name
     * @return the object or null
     */
    Constant findConstant(String constantName);

    /**
     * Try to find a user defined function with this name. This method returns
     * null if no object with this name exists.
     *
     * @param functionAlias the object name
     * @return the object or null
     */
    FunctionAlias findFunction(String functionAlias);

    /**
     * Release a unique object name.
     *
     * @param name the object name
     */
    void freeUniqueName(String name);

    /**
     * Create a unique constraint name.
     *
     * @param session the session
     * @param table the constraint table
     * @return the unique name
     */
    String getUniqueConstraintName(Session session, Table table);

    /**
     * Create a unique index name.
     *
     * @param session the session
     * @param table the indexed table
     * @param prefix the index name prefix
     * @return the unique name
     */
    String getUniqueIndexName(Session session, Table table, String prefix);

    /**
     * Get the table or view with the given name.
     * Local temporary tables are also returned.
     *
     * @param session the session
     * @param name the table or view name
     * @return the table or view
     * @throws org.h2.message.DbException if no such object exists
     */
    Table getTableOrView(Session session, String name);

    /**
     * Get the index with the given name.
     *
     * @param name the index name
     * @return the index
     * @throws DbException if no such object exists
     */
    Index getIndex(String name);

    /**
     * Get the constraint with the given name.
     *
     * @param name the constraint name
     * @return the constraint
     * @throws DbException if no such object exists
     */
    Constraint getConstraint(String name);

    /**
     * Get the user defined constant with the given name.
     *
     * @param constantName the constant name
     * @return the constant
     * @throws DbException if no such object exists
     */
    Constant getConstant(String constantName);

    /**
     * Get the sequence with the given name.
     *
     * @param sequenceName the sequence name
     * @return the sequence
     * @throws DbException if no such object exists
     */
    Sequence getSequence(String sequenceName);

    /**
     * Get all objects.
     *
     * @return a (possible empty) list of all objects
     */
    ArrayList<SchemaObject> getAll();

    /**
     * Get all objects of the given type.
     *
     * @param type the object type
     * @return a (possible empty) list of all objects
     */
    ArrayList<SchemaObject> getAll(int type);

    /**
     * Get all tables and views.
     *
     * @return a (possible empty) list of all objects
     */
    ArrayList<Table> getAllTablesAndViews();

    /**
     * Remove an object from this schema.
     *
     * @param obj the object to remove
     */
    void remove(SchemaObject obj);

    /**
     * Add a table to the schema.
     *
     * @param data the create table information
     * @return the created {@link Table} object
     */
    Table createTable(CreateTableData data);

    /**
     * Add a linked table to the schema.
     *
     * @param id the object id
     * @param tableName the table name of the alias
     * @param driver the driver class name
     * @param url the database URL
     * @param user the user name
     * @param password the password
     * @param originalSchema the schema name of the target table
     * @param originalTable the table name of the target table
     * @param emitUpdates if updates should be emitted instead of delete/insert
     * @param force create the object even if the database can not be accessed
     * @return the {@link org.h2.table.TableLink} object
     */
    TableLink createTableLink(
        int id, String tableName, String driver, String url, String user, String password,
        String originalSchema, String originalTable, boolean emitUpdates, boolean force
    );
}
