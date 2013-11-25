package org.h2.mac;

import org.h2.command.Parser;
import org.h2.engine.Session;
import org.h2.util.Utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
}
