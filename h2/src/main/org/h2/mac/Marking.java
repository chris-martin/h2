package org.h2.mac;

import org.h2.engine.Database;
import org.h2.util.New;
import org.h2.value.Value;
import org.h2.value.ValueString;

import java.util.*;

import static org.h2.mac.Queries.lines;
import static org.h2.mac.Queries.queryForInteger;
import static org.h2.message.DbException.throwInternalError;

public class Marking {

    public String sensitivity;

    public Set<String> compartments = New.hashSet();

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
        marking.sensitivity = scanner.next().trim().toUpperCase();
        if (!marking.sensitivity.matches("[A-Z0-9 ]+")) {
            throw throwInternalError("Illegal character in marking sensitivity");
        }

        // compartments
        if (!scanner.hasNext()) {
            throw throwInternalError("Marking must have at least one compartment");
        }
        while (scanner.hasNext()) {
            String compartment = scanner.next().trim();
            if (!compartment.matches("[A-Z0-9 ]+")) {
                throw throwInternalError("Illegal character in marking compartment");
            }
            marking.compartments.add(compartment);
        }

        return marking;
    }

    public String render() {

        if (sensitivity == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder(sensitivity);
        List<String> sortedCompartments = New.arrayList(compartments);
        Collections.sort(sortedCompartments);
        for (String compartment : sortedCompartments) {
            sb.append("/").append(compartment);
        }
        return sb.toString();
    }

    public int persist(Database database) {

        if (sensitivity == null) {
            return 0;
        }

        Integer sensitivityId = queryForInteger(
            database.getSystemSession(),
            lines(
                "select mac.sensitivity.sensitivity_id",
                "from mac.sensitivity",
                "where upper(mac.sensitivity.name) = upper(?)"
            ),
            Arrays.<Value>asList(
                ValueString.get(sensitivity)
            )
        );

        if (sensitivityId == null) {
            // todo
        }

        return 0; // todo
    }
}
