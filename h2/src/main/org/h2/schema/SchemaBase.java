package org.h2.schema;

import org.h2.engine.DbObjectBase;
import org.h2.engine.Session;
import org.h2.table.Table;
import org.h2.util.New;

import java.util.HashMap;

abstract class SchemaBase extends DbObjectBase implements Schema {

    protected void removeTablesAndViews(
        Session session,
        HashMap<String, ? extends Table> tablesAndViews
    ) {
        // There can be dependencies between tables e.g. using computed columns,
        // so we might need to loop over them multiple times.
        boolean runLoopAgain;
        do {
            runLoopAgain = false;
            if (tablesAndViews != null) {
                // Loop over a copy because the map is modified underneath us.
                for (Table obj : New.arrayList(tablesAndViews.values())) {
                    // Check for null because multiple tables might be deleted in one go
                    // underneath us.
                    if (obj.getName() != null) {
                        if (database.getDependentTable(obj, obj) == null) {
                            database.removeSchemaObject(session, obj);
                        } else {
                            runLoopAgain = true;
                        }
                    }
                }
            }
        } while (runLoopAgain);
    }

}
