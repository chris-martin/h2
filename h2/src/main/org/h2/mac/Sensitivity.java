package org.h2.mac;

import org.h2.engine.Session;
import org.h2.mac.SystemSessions.SystemTransaction;
import org.h2.value.ValueLong;
import org.h2.value.ValueString;

import static java.util.Objects.requireNonNull;
import static org.h2.mac.Queries.*;

public class Sensitivity {

    public Long id;

    public String name;

    public Sensitivity(String name) {
        this.name = name;
    }

    public long persist(SystemTransaction transaction) {

        Session session = transaction.getSystemSession();

        if (id != null) {
            return id;
        }

        id = selectLong(session, lines(
            "select mac.sensitivity.sensitivity_id",
            "from mac.sensitivity",
            "where upper(mac.sensitivity.name) = upper(?)"
        ), values(
            ValueString.get(name)
        ));

        if (id != null) {
            return id;
        }

        id = insertAndSelectLongIdentity(session, lines(
            "insert into mac.sensitivity ( name ) values ( ? )"
        ), values(
            ValueString.get(name)
        ));

        requireNonNull(id);

        insert(session, lines(
            "insert into mac.credential ( sensitivity_id, compartment_id )",
            "select",
            "  ? sensitivity_id,",
            "  mac.compartment.compartment_id",
            "from mac.compartment"
        ), values(
            ValueLong.get(id)
        ));

        return id;
    }

    @Override
    public String toString() {
        return "Sensitivity{" +
            "id=" + id +
            ", name='" + name + '\'' +
            '}';
    }
}
