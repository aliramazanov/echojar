package com.aliramazanov.echojar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aliramazanov.echojar.bootstrap.findings.Finding;
import com.aliramazanov.echojar.bootstrap.findings.Ledger;
import com.aliramazanov.echojar.fake.Db;
import com.aliramazanov.echojar.fake.FakeConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shop.LabServlet;
import shop.Servlets;

class RequestBoundaryIT {

    private static final String ITEMS = "SELECT * FROM request_item WHERE order_id = ?";

    private static void query(int count) throws Exception {
        try (
                Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(ITEMS)
        ) {
            for (int row = 0; row < count; row++) {
                statement.setInt(1, row);
                statement.executeQuery();
            }
        }
    }

    private static Connection connect() {
        return new FakeConnection();
    }

    private static Finding only() {
        List<Finding> findings = Ledger.findings();
        assertEquals(1, findings.size(), () -> "expected exactly one finding, got " + findings);
        Finding finding = findings.getFirst();
        assertNotNull(finding.site(), "a repeated statement must carry a call site");

        assertTrue(
                finding.site().toString().contains("LabServlet") ||
                        finding.site().toString().contains("RequestBoundaryIT"),
                "the call site must land in the test code, not the driver: " + finding.site()
        );

        return finding;
    }

    @BeforeEach
    void reset() {
        Ledger.reset();
        Db.reset();
    }

    @Test
    void aQueryPerConnectionIsStillOneRequest() {
        Servlets.request(LabServlet.reconnecting(RequestBoundaryIT::connect, 12));
        assertEquals(12, Db.count(ITEMS), "the driver saw twelve executions");

        assertEquals(
                12,
                only().peakPerLease(),
                "twelve connections in one request is one unit of twelve, not twelve units of one"
        );

        assertEquals(1, Ledger.leases(), "the request is the unit, not each connection");
    }

    @Test
    void theOutermostBoundaryIsTheOnlyOne() {
        Servlets.filteredRequest(LabServlet.reconnecting(RequestBoundaryIT::connect, 6));
        assertEquals(6, only().peakPerLease(), "a filter around a servlet is still one request");
        assertEquals(1, Ledger.leases());
    }

    @Test
    void repeatedRequestsEachCountSeparately() {
        for (int request = 0; request < 4; request++) {
            Servlets.request(LabServlet.reconnecting(RequestBoundaryIT::connect, 5));
        }

        Finding finding = only();
        assertEquals(5, finding.peakPerLease(), "the peak is one request, never the sum of them");
        assertEquals(20, finding.totalExecutions());
        assertEquals(4, finding.leases());
    }

    @Test
    void aRequestThatTouchesNothingIsNotAUnitOfWork() {
        Servlets.request(new LabServlet(() -> {
        }));

        assertEquals(
                0,
                Ledger.leases(),
                "an idle request would otherwise swamp the ones that query"
        );
    }

    @Test
    void aFailedRequestStillCloses() {
        assertThrows(
                IllegalStateException.class, () -> Servlets.request(new LabServlet(() -> {
                    query(4);
                    throw new IllegalStateException("handler blew up");
                }))
        );

        assertEquals(
                4,
                only().peakPerLease(),
                "what the request did before it failed still counts"
        );

        Servlets.request(LabServlet.reconnecting(RequestBoundaryIT::connect, 2));

        assertEquals(
                2,
                Ledger.leases(),
                "a thrown request must not leave its unit open on the thread"
        );
    }

    @Test
    void workHandedToAnotherThreadFallsBackToItsConnection() throws Exception {
        Thread worker = new Thread(() -> {
            try {
                query(7);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });

        Servlets.request(new LabServlet(() -> {
            worker.start();
            worker.join();
        }));

        assertEquals(
                7,
                only().peakPerLease(),
                "a thread with no boundary of its own is still counted, by its connection lease"
        );
    }
}
