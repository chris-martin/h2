package org.h2.mac;

import org.h2.engine.Database;
import org.h2.engine.Session;
import org.h2.mac.SystemSessions.SystemTransaction;
import org.h2.mac.SystemSessions.SystemTransactionAction;
import org.h2.result.ResultInterface;
import org.h2.util.New;
import org.h2.value.ValueLong;
import org.h2.value.ValueString;

import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static org.h2.mac.Queries.*;
import static org.h2.mac.SystemSessions.executeSystemTransaction;
import static org.h2.message.DbException.throwInternalError;

public class Marking {

    public Long id;

    public Sensitivity sensitivity;

    public Map<String, Compartment> compartments = New.hashMap();

    private static final String labelRegex = "[a-zA-Z0-9 \\-_]+";

    public Marking() { }

    public Marking(Sensitivity sensitivity, Compartment... compartments) {
        setSensitivity(sensitivity);
        setCompartments(asList(compartments));
    }

    public void setSensitivity(Sensitivity sensitivity) {
        this.sensitivity = sensitivity;
    }

    public void setCompartments(Collection<Compartment> compartments) {
        for (Compartment compartment : compartments) {
            this.compartments.put(compartment.name, compartment);
        }
    }

    public static Marking parse(String markingString) {

        Marking marking = new Marking();

        if (markingString.isEmpty()) {
            return marking;
        }

        Scanner scanner = new Scanner(markingString).useDelimiter("/*");

        // sensitivity
        if (!scanner.hasNext()) {
            throw throwInternalError("Marking must begin with a sensitivity");
        }
        String sensitivityName = scanner.next().trim().toUpperCase();
        if (!sensitivityName.matches(labelRegex)) {
            throw throwInternalError("Illegal character in marking sensitivity");
        }
        marking.sensitivity = new Sensitivity(sensitivityName);

        // compartments
        if (!scanner.hasNext()) {
            throw throwInternalError("Marking must have at least one compartment");
        }
        while (scanner.hasNext()) {
            String compartmentName = scanner.next().trim();
            if (!compartmentName.matches(labelRegex)) {
                throw throwInternalError("Illegal character in marking compartment");
            }
            marking.compartments.put(compartmentName, new Compartment(compartmentName));
        }

        return marking;
    }

    public static String render(Session session, final Long markingId) {

        // todo do a permission check to make sure this is a marking the user can see

        return executeSystemTransaction(session.getDatabase(), new SystemTransactionAction<String>() {
            @Override
            public String execute(SystemTransaction transaction) {

                Session session = transaction.getSystemSession();

                Marking marking = new Marking();

                marking.setSensitivity(new Sensitivity(selectString(session, lines(
                    "select mac.sensitivity.name",
                    "from mac.sensitivity",
                    "join mac.marking",
                    "on mac.sensitivity.sensitivity_id = mac.marking.sensitivity_id",
                    "where mac.marking.marking_id = ?"
                ), values(
                    ValueLong.get(markingId)
                ))));

                marking.setCompartments(New.hashSet(selectList(session, lines(
                    "select mac.compartment.name",
                    "from mac.compartment",
                    "join mac.marking_compartment",
                    "on mac.compartment.compartment_id = mac.marking_compartment.compartment_id",
                    "where mac.marking_compartment.marking_id = ?"
                ), values(
                    ValueLong.get(markingId)
                ), new RowMapper<Compartment>() {
                    @Override
                    public Compartment apply(ResultInterface result) {
                        return new Compartment(result.currentRow()[0].getString());
                    }
                })));

                return marking.render();
            }
        });
    }

    public String render() {

        if (sensitivity == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder(sensitivity.name);
        for (String compartment : sortedCompartmentNames()) {
            sb.append("/").append(compartment);
        }
        return sb.toString();
    }

    private List<String> sortedCompartmentNames() {
        List<String> names = New.arrayList(compartments.keySet());
        Collections.sort(names);
        return names;
    }

    public long persist(Database database) {

        if (sensitivity == null) {
            id = 0L;
        } else {
            executeSystemTransaction(database, new SystemTransactionAction<Long>() {
                @Override
                public Long execute(SystemTransaction transaction) {
                    return persist(transaction);
                }
            });
        }

        return requireNonNull(id);
    }

    public long persist(SystemTransaction transaction) {

        if (id != null) {
            return id;
        }

        if (sensitivity == null) {
            id = 0L;
            return id;
        }

        Session session = transaction.getSystemSession();

        sensitivity.persist(transaction);

        for (Compartment compartment : compartments.values()) {
            compartment.persist(transaction);
        }

        String compartmentIdListString = buildCompartmentIdListString();

        id = selectLong(session, lines(
            "select mac.marking.marking_id",
            "from mac.marking",
            "where mac.marking.sensitivity_id = ?",
            "and mac.marking.compartment_id_list = ?"
        ), values(
            ValueLong.get(sensitivity.id),
            ValueString.get(compartmentIdListString)
        ));

        if (id == null) {

            id = insertAndSelectLongIdentity(session, lines(
                "insert into mac.marking ( sensitivity_id, compartment_id_list )",
                "values ( ?, ? )"
            ), values(
                ValueLong.get(sensitivity.id),
                ValueString.get(compartmentIdListString)
            ));

            requireNonNull(id);

            for (Compartment compartment : compartments.values()) {
                insert(session, lines(
                    "insert into mac.marking_compartment ( marking_id, compartment_id )",
                    "values ( ?, ? )"
                ), values(
                    ValueLong.get(id),
                    ValueLong.get(compartment.id)
                ));
            }
        }

        return requireNonNull(id);
    }

    private String buildCompartmentIdListString() {

        ArrayList<Long> compartmentIds = New.arrayList();

        for (Compartment compartment : compartments.values()) {
            compartmentIds.add(requireNonNull(compartment.id));
        }

        Collections.sort(compartmentIds);
        StringBuilder sb = new StringBuilder();
        Iterator<Long> it = compartmentIds.iterator();
        while (it.hasNext()) {
            sb.append(Long.toString(it.next()));
            if (it.hasNext()) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "Marking{" +
            "id=" + id +
            ", sensitivity=" + sensitivity +
            ", compartments=" + compartments +
            '}';
    }
}
