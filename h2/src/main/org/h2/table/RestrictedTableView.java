package org.h2.table;

import org.h2.engine.Session;
import org.h2.expression.Parameter;
import org.h2.index.Index;
import org.h2.index.IndexType;
import org.h2.message.DbException;
import org.h2.result.Row;
import org.h2.schema.Schema;

import java.util.ArrayList;

public class RestrictedTableView extends TableView {

    public RestrictedTableView(Schema schema, int id, String name, String querySQL,
    ArrayList<Parameter> params, String[] columnNames, Session session, boolean recursive) {

        super(schema, id, name, querySQL, params, columnNames, session, recursive);
    }

    private Table shadowTable() {
        return getSchema().asRestricted().getShadowTable(this);
    }

    @Override
    public Index addIndex(Session session, String indexName, int indexId,
    IndexColumn[] cols, IndexType indexType, boolean create, String indexComment) {

        for (IndexColumn indexColumn : cols) {
            indexColumn.column = shadowTable().getColumn(indexColumn.column.getName());
        }

        return shadowTable().addIndex(
            session, indexName, indexId, cols, indexType, create, indexComment);
    }

    @Override
    public void addRow(Session session, Row row) {
        shadowTable().addRow(session, row);
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

    @Override
    public Column getColumn(String columnName) {
        return shadowTable().getColumn(columnName);
    }

    @Override
    public Column getColumn(int index) {
        return shadowTable().getColumn(index);
    }

    @Override
    public Column[] getColumns() {
        return shadowTable().getColumns();
    }

    @Override
    public Row getTemplateRow() {
        return shadowTable().getTemplateRow();
    }
}
