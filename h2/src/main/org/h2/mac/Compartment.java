package org.h2.mac;

import org.h2.engine.Session;
import org.h2.mac.SystemSessions.SystemTransaction;
import org.h2.value.ValueLong;
import org.h2.value.ValueString;

import static java.util.Objects.requireNonNull;
import static org.h2.mac.Queries.*;

public class Compartment {

    public Long id;

    public String name;

    public Compartment(String name) {
        this.name = name;
    }

    public long persist(SystemTransaction transaction) {

        Session session = transaction.getSystemSession();

        if (id != null) {
            return id;
        }

        id = selectLong(session, lines(
            "select mac.compartment.compartment_id",
            "from mac.compartment",
            "where upper(mac.compartment.name) = upper(?)"
        ), values(
            ValueString.get(name)
        ));

        if (id != null) {
            return id;
        }

        id = insertAndSelectLongIdentity(session, lines(
            "insert into mac.compartment ( name ) values ( ? )"
        ), values(
            ValueString.get(name)
        ));

        requireNonNull(id);

        insert(session, lines(
            "insert into mac.credential ( sensitivity_id, compartment_id )",
            "select",
            "  mac.sensitivity.sensitivity_id,",
            "  ? compartment_id",
            "from mac.sensitivity"
        ), values(
            ValueLong.get(id)
        ));

        return id;
    }

    @Override
    public String toString() {
        return "Compartment{" +
            "id=" + id +
            ", name='" + name + '\'' +
            '}';
    }
}
