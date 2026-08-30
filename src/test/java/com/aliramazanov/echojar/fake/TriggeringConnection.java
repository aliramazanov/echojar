package com.aliramazanov.echojar.fake;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class TriggeringConnection extends FakeConnection {

    public static final String TRIGGERED = "INSERT INTO fake_audit (note) VALUES (?)";

    @Override
    public PreparedStatement prepareStatement(String sql) {
        if (TRIGGERED.equals(sql)) {
            return super.prepareStatement(sql);
        }
        return new Triggering(this, sql);
    }

    private static final class Triggering extends FakePreparedStatement {

        private final TriggeringConnection owner;

        private Triggering(TriggeringConnection owner, String sql) {
            super(owner, sql);
            this.owner = owner;
        }

        @Override
        public int executeUpdate() throws SQLException {
            int updated = super.executeUpdate();
            fire();
            return updated;
        }

        private void fire() throws SQLException {
            try (PreparedStatement audit = owner.prepareStatement(TRIGGERED)) {
                audit.setString(1, "fired");
                audit.executeUpdate();
            }
        }
    }
}
