package org.h2.mac;

import org.h2.command.Parser;
import org.h2.engine.Session;
import org.h2.engine.User;
import org.h2.mac.SystemSessions.SystemTransaction;
import org.h2.mac.SystemSessions.SystemTransactionAction;
import org.h2.util.Utils;
import org.h2.value.ValueLong;
import org.h2.value.ValueString;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.h2.mac.Queries.*;
import static org.h2.mac.SystemSessions.executeSystemTransaction;
import static org.h2.message.DbException.throwInternalError;

public final class Mac {

    private Mac() { }

    public static final String MAC_SCHEMA_NAME = "MAC";

    public static void initializeMacSchema(Session session) {
        new Parser(session).prepareCommand(resource("/org/h2/mac/mac-init.sql")).update();
    }

    private static String resource(String name) {
        try {
            return new String(Utils.getResource(name), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void grant(Session session, String markingString, final User grantee) {

        final Marking marking = Marking.parse(markingString);

        // todo check permissions

        executeSystemTransaction(session.getDatabase(), new SystemTransactionAction<Void>() {

            @Override
            public Void execute(SystemTransaction transaction) {

                Session session = transaction.getSystemSession();

                if (marking.sensitivity == null) {
                    throw throwInternalError("Granted credential must have a sensitivity");
                }

                Sensitivity sensitivity = marking.sensitivity;

                if (marking.compartments.size() != 1) {
                    throw throwInternalError("Granted credential must have exactly one compartment");
                }

                Compartment compartment = marking.compartments.iterator().next();

                sensitivity.persist(transaction);
                compartment.persist(transaction);

                insert(session, lines(
                    // todo don't try to re-insert duplicate rows
                    // todo also insert lower sensitivities
                    "insert into mac.user_credential ( user_name, credential_id )",
                    "select ?, mac.credential.credential_id",
                    "from mac.credential",
                    "where mac.credential.sensitivity_id = ?",
                    "and mac.credential.compartment_id = ?"
                ), values(
                    ValueString.get(grantee.getName()),
                    ValueLong.get(sensitivity.id),
                    ValueLong.get(compartment.id)
                ));

                return null;
            }
        });
    }

    public static void revoke(Session session, String markingString, User grantee) {

        // todo check permissions

        // todo revoke access
    }

}
