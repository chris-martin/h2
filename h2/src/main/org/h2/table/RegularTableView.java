package org.h2.table;

import org.h2.engine.Session;
import org.h2.expression.Parameter;
import org.h2.index.Index;
import org.h2.index.IndexType;
import org.h2.message.DbException;
import org.h2.result.Row;
import org.h2.schema.Schema;

import java.util.ArrayList;

public class RegularTableView extends TableView {

    public RegularTableView(Schema schema, int id, String name, String querySQL,
    ArrayList<Parameter> params, String[] columnNames, Session session, boolean recursive) {

        super(schema, id, name, querySQL, params, columnNames, session, recursive);
    }

    @Override
    public void addRow(Session session, Row row) {
        throw DbException.getUnsupportedException("VIEW");
    }

    @Override
    public Index addIndex(Session session, String indexName, int indexId,
    IndexColumn[] cols, IndexType indexType, boolean create, String indexComment) {

        throw DbException.getUnsupportedException("VIEW");
    }

    @Override
    public void removeRow(Session session, Row row) {
        throw DbException.getUnsupportedException("VIEW");
    }

    @Override
    public void checkSupportAlter() {
        throw DbException.getUnsupportedException("VIEW");
    }

    @Override
    public void truncate(Session session) {
        throw DbException.getUnsupportedException("VIEW");
    }
}
