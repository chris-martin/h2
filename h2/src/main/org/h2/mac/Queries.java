package org.h2.mac;

import org.h2.command.Parser;
import org.h2.command.Prepared;
import org.h2.engine.Session;
import org.h2.result.ResultInterface;
import org.h2.value.Value;

import java.util.List;

import static org.h2.message.DbException.throwInternalError;

public final class Queries {

    private Queries() { }

    public static Value queryForValue(Session session, String sql, List<Value> parameters) {

        Prepared prepared = new Parser(session).prepare(sql);
        for (int i = 0; i < parameters.size(); i++) {
            prepared.getParameters().get(i).setValue(parameters.get(i));
        }
        ResultInterface result = prepared.query(2);

        try {
            if (!result.next()) {
                return null;
            }
            Value value = result.currentRow()[0];
            if (result.next()) {
                throw throwInternalError("Unexpected multiple results from query");
            }
            return value;
        } finally {
            result.close();
        }
    }

    public static Integer queryForInteger(Session session, String sql, List<Value> parameters) {

        Value value = queryForValue(session, sql, parameters);

        switch (value.getType()) {
            case Value.NULL:
                return null;
            case Value.INT:
                return value.getInt();
            default:
                throw throwInternalError("Expected int or null from query");
        }
    }

    public static String lines(String... strings) {

        StringBuilder sb = new StringBuilder();
        for (String s : strings) {
            sb.append(s).append("\n");
        }
        return sb.toString();
    }

}
