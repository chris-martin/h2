package org.h2.mac;

import org.h2.command.Parser;
import org.h2.engine.Database;
import org.h2.expression.Parameter;
import org.h2.util.New;

import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import static java.util.Arrays.asList;
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

        return 0; // todo
/*

        new Parser(database.getSystemSession()).prepare(
            "select mac.sensitivity.sensitivity_id from mac.sensitivity where upper(mac.sensitivity.name) = upper(?)"
        ).;*/
    }
}
