/*
 * Copyright 2004-2008 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.table;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashSet;
import org.h2.api.DatabaseEventListener;
import org.h2.constant.ErrorCode;
import org.h2.constant.SysProperties;
import org.h2.constraint.Constraint;
import org.h2.constraint.ConstraintReferential;
import org.h2.engine.Constants;
import org.h2.engine.DbObject;
import org.h2.engine.Session;
import org.h2.index.BtreeIndex;
import org.h2.index.Cursor;
import org.h2.index.HashIndex;
import org.h2.index.Index;
import org.h2.index.IndexType;
import org.h2.index.LinearHashIndex;
import org.h2.index.MultiVersionIndex;
import org.h2.index.ScanIndex;
import org.h2.index.TreeIndex;
import org.h2.message.Message;
import org.h2.message.Trace;
import org.h2.result.Row;
import org.h2.schema.Schema;
import org.h2.store.DataPage;
import org.h2.store.Record;
import org.h2.store.RecordReader;
import org.h2.util.MathUtils;
import org.h2.util.ObjectArray;
import org.h2.util.StringUtils;
import org.h2.value.DataType;
import org.h2.value.Value;

/**
 * Most tables are an instance of this class.
 * For this table, the data is stored in the database. The actual data is not kept here,
 * instead it is kept in the indexes. There is at least one index, the scan index.
 */
public class TableData extends Table implements RecordReader {
    private final boolean clustered;
    private ScanIndex scanIndex;
    private long rowCount;
    private Session lockExclusive;
    private HashSet lockShared = new HashSet();
    private Trace traceLock;
    private boolean globalTemporary;
    private final ObjectArray indexes = new ObjectArray();
    private long lastModificationId;
    private boolean containsLargeObject;

    public TableData(Schema schema, String tableName, int id, ObjectArray columns,
            boolean persistent, boolean clustered) throws SQLException {
        super(schema, id, tableName, persistent);
        Column[] cols = new Column[columns.size()];
        columns.toArray(cols);
        setColumns(cols);
        this.clustered = clustered;
        if (!clustered) {
            scanIndex = new ScanIndex(this, id, IndexColumn.wrap(cols), IndexType.createScan(persistent));
            indexes.add(scanIndex);
        }
        for (int i = 0; i < cols.length; i++) {
            if (DataType.isLargeObject(cols[i].getType())) {
                containsLargeObject = true;
            }
        }
        traceLock = database.getTrace(Trace.LOCK);
    }

    public void close(Session session) throws SQLException {
        for (int i = 0; i < indexes.size(); i++) {
            Index index = (Index) indexes.get(i);
            index.close(session);
        }
    }

    public Row getRow(Session session, int key) throws SQLException {
        return scanIndex.getRow(session, key);
    }

    public void addRow(Session session, Row row) throws SQLException {
        int i = 0;
        lastModificationId = database.getNextModificationDataId();
        if (database.isMultiVersion()) {
            row.setSessionId(session.getId());
        }
        try {
            for (; i < indexes.size(); i++) {
                Index index = (Index) indexes.get(i);
                index.add(session, row);
                if (SysProperties.CHECK) {
                    if (!database.isMultiVersion()) {
                        long rc = index.getRowCount(session);
                        if (rc != rowCount + 1) {
                            throw Message.getInternalError("rowCount expected " + (rowCount + 1) + " got " + rc);
                        }
                    }
                }
            }
            rowCount++;
        } catch (Throwable e) {
            try {
                while (--i >= 0) {
                    Index index = (Index) indexes.get(i);
                    index.remove(session, row);
                    if (SysProperties.CHECK) {
                        if (!database.isMultiVersion()) {
                            long rc = index.getRowCount(session);
                            if (rc != rowCount) {
                                throw Message.getInternalError("rowCount expected " + (rowCount) + " got " + rc);
                            }
                        }
                    }
                }
            } catch (SQLException e2) {
                // this could happen, for example on failure in the storage
                // but if that is not the case it means there is something wrong
                // with the database
                // TODO log this problem
                throw e2;
            }
            throw Message.convert(e);
        }
    }

    public Index getScanIndex(Session session) {
        return (Index) indexes.get(0);
    }

    public Index getUniqueIndex() {
        for (int i = 0; i < indexes.size(); i++) {
            Index idx = (Index) indexes.get(i);
            if (idx.getIndexType().isUnique()) {
                return idx;
            }
        }
        return null;
    }

    public ObjectArray getIndexes() {
        return indexes;
    }

    public Index addIndex(Session session, String indexName, int indexId, IndexColumn[] cols, IndexType indexType,
            int headPos, String indexComment) throws SQLException {
        if (indexType.isPrimaryKey()) {
            indexName = getSchema().getUniqueIndexName(this, Constants.PRIMARY_KEY_PREFIX);
            for (int i = 0; i < cols.length; i++) {
                Column column = cols[i].column;
                if (column.getNullable()) {
                    throw Message.getSQLException(ErrorCode.COLUMN_MUST_NOT_BE_NULLABLE_1, column.getName());
                }
                column.setPrimaryKey(true);
            }
        }
        Index index;
        if (isPersistent() && indexType.isPersistent()) {
            if (indexType.isHash()) {
                index = new LinearHashIndex(session, this, indexId, indexName, cols, indexType);
            } else {
                index = new BtreeIndex(session, this, indexId, indexName, cols, indexType, headPos);
            }
        } else {
            if (indexType.isHash()) {
                index = new HashIndex(this, indexId, indexName, cols, indexType);
            } else {
                index = new TreeIndex(this, indexId, indexName, cols, indexType);
            }
        }
        if (database.isMultiVersion()) {
            index = new MultiVersionIndex(index, this);
        }
        if (index.needRebuild() && rowCount > 0) {
            try {
                Index scan = getScanIndex(session);
                long remaining = scan.getRowCount(session);
                long total = remaining;
                Cursor cursor = scan.find(session, null, null);
                long i = 0;
                int bufferSize = Constants.DEFAULT_MAX_MEMORY_ROWS;
                ObjectArray buffer = new ObjectArray(bufferSize);
                while (cursor.next()) {
                    database.setProgress(DatabaseEventListener.STATE_CREATE_INDEX, getName(), MathUtils
                            .convertLongToInt(i++), MathUtils.convertLongToInt(total));
                    Row row = cursor.get();
                    // index.add(session, row);
                    buffer.add(row);
                    if (buffer.size() >= bufferSize) {
                        addRowsToIndex(session, buffer, index);
                    }
                    remaining--;
                }
                addRowsToIndex(session, buffer, index);
                if (SysProperties.CHECK && remaining != 0) {
                    throw Message.getInternalError("rowcount remaining=" + remaining + " " + getName());
                }
            } catch (SQLException e) {
                try {
                    index.remove(session);
                } catch (SQLException e2) {
                    // this could happen, for example on failure in the storage
                    // but if that is not the case it means there is something wrong with the database
                    // TODO log this problem
                    throw e2;
                }
                throw e;
            }
        }
        boolean temporary = getTemporary();
        index.setTemporary(temporary);
        if (index.getCreateSQL() != null) {
            index.setComment(indexComment);
            database.addSchemaObject(session, index);
            // Need to update, because maybe the index is rebuilt at startup,
            // and so the head pos may have changed, which needs to be stored now.
            // addSchemaObject doesn't update the sys table at startup
            if (index.getIndexType().isPersistent() && !database.getReadOnly()
                    && !database.getLog().containsInDoubtTransactions()) {
                // can not save anything in the log file if it contains in-doubt transactions
                database.update(session, index);
            }
        }
        indexes.add(index);
        setModified();
        return index;
    }

    public boolean canGetRowCount() {
        return true;
    }

    private void addRowsToIndex(Session session, ObjectArray list, Index index) throws SQLException {
        final Index idx = index;
        try {
            list.sort(new Comparator() {
                public int compare(Object o1, Object o2) {
                    Row r1 = (Row) o1;
                    Row r2 = (Row) o2;
                    try {
                        return idx.compareRows(r1, r2);
                    } catch (SQLException e) {
                        throw Message.convertToInternal(e);
                    }
                }
            });
        } catch (Exception e) {
            throw Message.convert(e);
        }
        for (int i = 0; i < list.size(); i++) {
            Row r = (Row) list.get(i);
            index.add(session, r);
        }
        list.clear();
    }

    public boolean canDrop() {
        return true;
    }

    public long getRowCount(Session session) {
        if (database.isMultiVersion()) {
            return getScanIndex(session).getRowCount(session);
        }
        return rowCount;
    }

    public void removeRow(Session session, Row row) throws SQLException {
        lastModificationId = database.getNextModificationDataId();
        if (database.isMultiVersion()) {
            if (row.getDeleted()) {
                throw Message.getSQLException(ErrorCode.CONCURRENT_UPDATE_1, getName());
            }
            int old = row.getSessionId();
            int newId = session.getId();
            if (old == 0) {
                row.setSessionId(newId);
            } else if (old != newId) {
                throw Message.getSQLException(ErrorCode.CONCURRENT_UPDATE_1, getName());
            }
        }
        for (int i = indexes.size() - 1; i >= 0; i--) {
            Index index = (Index) indexes.get(i);
            index.remove(session, row);
            if (SysProperties.CHECK) {
                if (!database.isMultiVersion()) {
                    long rc = index.getRowCount(session);
                    if (rc != rowCount - 1) {
                        throw Message.getInternalError("rowCount expected " + (rowCount - 1) + " got " + rc);
                    }
                }
            }
        }
        rowCount--;
    }

    public void truncate(Session session) throws SQLException {
        lastModificationId = database.getNextModificationDataId();
        for (int i = indexes.size() - 1; i >= 0; i--) {
            Index index = (Index) indexes.get(i);
            index.truncate(session);
            if (SysProperties.CHECK) {
                long rc = index.getRowCount(session);
                if (rc != 0) {
                    throw Message.getInternalError("rowCount expected 0 got " + rc);
                }
            }
        }
        rowCount = 0;
    }

    public boolean isLockExclusive(Session s) {
        return lockExclusive == s;
    }

    public void lock(Session session, boolean exclusive, boolean force) throws SQLException {
        int lockMode = database.getLockMode();
        if (lockMode == Constants.LOCK_MODE_OFF) {
            return;
        }
        long max = System.currentTimeMillis() + session.getLockTimeout();
        if (!force && database.isMultiVersion()) {
            // MVCC: update, delete, and insert use a shared lock
            // select doesn't lock
            if (exclusive) {
                exclusive = false;
            } else {
                return;
            }
        }
        synchronized (database) {
            while (true) {
                if (lockExclusive == session) {
                    return;
                }
                if (exclusive) {
                    if (lockExclusive == null) {
                        if (lockShared.isEmpty()) {
                            traceLock(session, exclusive, "added for");
                            session.addLock(this);
                            lockExclusive = session;
                            return;
                        } else if (lockShared.size() == 1 && lockShared.contains(session)) {
                            traceLock(session, exclusive, "add (upgraded) for ");
                            lockExclusive = session;
                            return;
                        }
                    }
                } else {
                    if (lockExclusive == null) {
                        if (lockMode == Constants.LOCK_MODE_READ_COMMITTED && !database.getMultiThreaded() && !database.isMultiVersion()) {
                            // READ_COMMITTED read locks are acquired but they
                            // are released immediately
                            // when allowing only one thread, no read locks are
                            // required
                            return;
                        } else if (!lockShared.contains(session)) {
                            traceLock(session, exclusive, "ok");
                            session.addLock(this);
                            lockShared.add(session);
                        }
                        return;
                    }
                }
                long now = System.currentTimeMillis();
                if (now >= max) {
                    traceLock(session, exclusive, "timeout after " + session.getLockTimeout());
                    throw Message.getSQLException(ErrorCode.LOCK_TIMEOUT_1, getName());
                }
                try {
                    traceLock(session, exclusive, "waiting for");
                    if (database.getLockMode() == Constants.LOCK_MODE_TABLE_GC) {
                        for (int i = 0; i < 20; i++) {
                            long free = Runtime.getRuntime().freeMemory();
                            System.gc();
                            long free2 = Runtime.getRuntime().freeMemory();
                            if (free == free2) {
                                break;
                            }
                        }
                    }
                    database.wait(max - now);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
    }

    private void traceLock(Session session, boolean exclusive, String s) {
        if (traceLock.debug()) {
            traceLock.debug(session.getId() + " " + (exclusive ? "exclusive write lock" : "shared read lock") + " " + s + " " + getName());
        }
    }

    public String getDropSQL() {
        return "DROP TABLE IF EXISTS " + getSQL();
    }

    public String getCreateSQL() {
        StringBuffer buff = new StringBuffer();
        buff.append("CREATE ");
        if (getTemporary()) {
            if (globalTemporary) {
                buff.append("GLOBAL ");
            } else {
                buff.append("LOCAL ");
            }
            buff.append("TEMPORARY ");
        } else if (isPersistent()) {
            buff.append("CACHED ");
        } else {
            buff.append("MEMORY ");
        }
        buff.append("TABLE ");
        buff.append(getSQL());
        if (comment != null) {
            buff.append(" COMMENT ");
            buff.append(StringUtils.quoteStringSQL(comment));
        }
        buff.append("(\n    ");
        for (int i = 0; i < columns.length; i++) {
            Column column = columns[i];
            if (i > 0) {
                buff.append(",\n    ");
            }
            buff.append(column.getCreateSQL());
        }
        buff.append("\n)");
        return buff.toString();
    }

    public boolean isLockedExclusively() {
        return lockExclusive != null;
    }

    public void unlock(Session s) {
        if (database != null) {
            traceLock(s, lockExclusive == s, "unlock");
            if (lockExclusive == s) {
                lockExclusive = null;
            }
            if (lockShared.size() > 0) {
                lockShared.remove(s);
            }
            // TODO lock: maybe we need we fifo-queue to make sure nobody
            // starves. check what other databases do
            synchronized (database) {
                if (database.getSessionCount() > 1) {
                    database.notifyAll();
                }
            }
        }
    }

    public Record read(Session session, DataPage s) throws SQLException {
        int len = s.readInt();
        Value[] data = new Value[len];
        for (int i = 0; i < len; i++) {
            data[i] = s.readValue();
        }
        int memory = containsLargeObject ? Row.MEMORY_CALCULATE : memoryPerRow;
        Row row = new Row(data, memory);
        return row;
    }

    public void setRowCount(int count) {
        this.rowCount = count;
    }

    public void removeChildrenAndResources(Session session) throws SQLException {
        super.removeChildrenAndResources(session);
        // go backwards because database.removeIndex will call table.removeIndex
        while (indexes.size() > 1) {
            Index index = (Index) indexes.get(1);
            if (index.getName() != null) {
                database.removeSchemaObject(session, index);
            }
        }
        if (SysProperties.CHECK) {
            ObjectArray list = database.getAllSchemaObjects(DbObject.INDEX);
            for (int i = 0; i < list.size(); i++) {
                Index index = (Index) list.get(i);
                if (index.getTable() == this) {
                    throw Message.getInternalError("index not dropped: " + index.getName());
                }
            }
        }
        scanIndex.remove(session);
        scanIndex = null;
        lockExclusive = null;
        lockShared = null;
        invalidate();
    }

    public void checkRename() throws SQLException {
    }

    public void checkSupportAlter() throws SQLException {
    }

    public boolean canTruncate() {
        ObjectArray constraints = getConstraints();
        for (int i = 0; constraints != null && i < constraints.size(); i++) {
            Constraint c = (Constraint) constraints.get(i);
            if (!(c.getConstraintType().equals(Constraint.REFERENTIAL))) {
                continue;
            }
            ConstraintReferential ref = (ConstraintReferential) c;
            if (ref.getRefTable() == this) {
                return false;
            }
        }
        return true;
    }

    public String getTableType() {
        return Table.TABLE;
    }

    public void setGlobalTemporary(boolean globalTemporary) {
        this.globalTemporary = globalTemporary;
    }

    public boolean getGlobalTemporary() {
        return globalTemporary;
    }

    public long getMaxDataModificationId() {
        return lastModificationId;
    }

    public boolean isClustered() {
        return clustered;
    }

    public boolean getContainsLargeObject() {
        return containsLargeObject;
    }

}
