package org.h2.mac;

import org.h2.engine.Database;
import org.h2.engine.Session;

public final class SystemSessions {

    private SystemSessions() { }

    public static final class SystemTransaction {

        private final Session systemSession;

        private SystemTransaction(Session systemSession) {
            this.systemSession = systemSession;
        }

        public Session getSystemSession() {
            return systemSession;
        }
    }

    public interface SystemTransactionAction<T> {

        T execute(SystemTransaction transaction);
    }

    public static <T> T executeSystemTransaction(
        Database database,
        SystemTransactionAction<T> action
    ) {
        Session session = database.getSystemSession();
        SystemTransaction transaction = new SystemTransaction(session);

        synchronized (session) {
            try {
                session.commit(true);
                session.begin();
                T result = action.execute(transaction);
                session.commit(true);
                return result;
            } finally {
                session.rollback();
            }
        }
    }
}
