package com.aliramazanov.echojar.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aliramazanov.echojar.bootstrap.findings.CallSite;
import com.aliramazanov.echojar.bootstrap.findings.Echoes;
import com.aliramazanov.echojar.bootstrap.findings.Lease;
import com.aliramazanov.echojar.bootstrap.findings.Ledger;
import com.aliramazanov.echojar.bootstrap.findings.SqlTemplate;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportTest {

    @BeforeEach
    void reset() {
        Ledger.reset();
    }

    @Test
    void anEmptyLedgerSaysSo() {
        String output = render(5, detector("threshold=5"));
        assertTrue(output.contains("no echoes in 0 connection leases"), output);
    }

    @Test
    void findingsBelowTheThresholdAreNotPrinted() {
        record(new SqlTemplate("SELECT quiet", 1, false), 2, null);
        String output = render(5, detector("threshold=5"));
        assertFalse(output.contains("SELECT quiet"), output);
        assertTrue(output.contains("no echoes in 1 connection leases"), output);
    }

    @Test
    void anUnresolvedCallSiteIsStated() {
        record(new SqlTemplate("SELECT loud", 1, false), 9, null);
        String output = render(5, detector("threshold=5"));
        assertTrue(output.contains("SELECT loud"), output);
        assertTrue(output.contains("9 executions in one connection lease"), output);
        assertTrue(output.contains("call site not resolved"), output);
    }

    @Test
    void theCallSiteCountNeverExceedsTheSitesActuallyResolved() {
        record(new SqlTemplate("SELECT a", 1, false), 9, null);
        record(new SqlTemplate("SELECT b", 2, false), 9, null);
        String output = render(5, detector("threshold=5"));
        assertFalse(output.contains("2 call sites"),
                "no call site was resolved, so claiming two is a lie:\n" + output);
    }

    @Test
    void repeatedLeasesAreSummarised() {
        SqlTemplate template = new SqlTemplate("SELECT repeated", 1, false);
        record(template, 6, new CallSite("shop.Service", "load", "Service.java", 12));
        record(template, 8, new CallSite("shop.Service", "load", "Service.java", 12));
        String output = render(5, detector("threshold=5"));
        assertTrue(output.contains("8 executions in one connection lease"),
                "the peak lease is what matters:\n" + output);
        assertTrue(output.contains("seen in 2 connection leases, 14 executions total"), output);
        assertTrue(output.contains("Service.load(Service.java:12)"), output);
    }

    @Test
    void anOverflowingTemplateCacheIsAnnounced() {
        Detector detector = detector("threshold=5,templates=1");
        detector.template("SELECT one FROM t");
        detector.template("SELECT two FROM t");
        detector.template("SELECT three FROM t");
        String output = render(5, detector);
        assertTrue(output.contains("were not tracked"), "an overflow must be visible:\n" + output);
    }

    @Test
    void aFloodOfEchoesIsTruncatedAndSaysSo() {
        for (int template = 1; template <= 60; template++) {
            record(new SqlTemplate("SELECT c" + template, template, false), 10 + template, null);
        }
        String output = render(5, detector("threshold=5"));
        assertTrue(output.contains("and 35 more"), "the omission must be stated:\n" + output);
        assertTrue(output.contains("SELECT c60"), "the loudest echo must survive truncation");
        assertFalse(output.contains("SELECT c1\n"), "the quietest echo is the one dropped");
    }

    @Test
    void statementsSpreadOverManyLeasesAreShownSeparately() {
        SqlTemplate perRequest = new SqlTemplate("SELECT one FROM t WHERE id = ?", 1, false);
        for (int lease = 0; lease < 40; lease++) {
            record(perRequest, 1, new CallSite("shop.Dao", "lookup", "Dao.java", 9));
        }
        String output = render(5, detector("threshold=5"));
        assertTrue(output.contains("one or two queries per connection lease"), output);
        assertTrue(output.contains("40 executions across 40 connection leases"), output);
        assertTrue(output.contains("Dao.lookup(Dao.java:9)"), output);
        assertTrue(output.contains("cannot tell them apart"),
                "the report must not claim this is definitely a fault:\n" + output);
    }

    @Test
    void aStatementThatRepeatsInsideOneLeaseIsAnEchoNotSpread() {
        SqlTemplate loop = new SqlTemplate("SELECT many FROM t WHERE id = ?", 2, false);
        record(loop, 30, new CallSite("shop.Dao", "loop", "Dao.java", 20));
        String output = render(5, detector("threshold=5"));
        assertTrue(output.contains("30 executions in one connection lease"), output);
        assertFalse(output.contains("one or two queries per connection lease"),
                "a real echo must not also be listed as spread:\n" + output);
    }

    @Test
    void aFindingThatHasNotBeenSeenLatelySaysSo() {
        record(new SqlTemplate("SELECT stale FROM t WHERE id = ?", 1, false), 9, null);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new Report(5, detector("threshold=5"), false, 0)
                .print(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String output = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("last seen"),
                "an old finding must not look like something happening now:\n" + output);
    }

    @Test
    void aFreshFindingIsNotLabelledStale() {
        record(new SqlTemplate("SELECT fresh FROM t WHERE id = ?", 2, false), 9, null);
        String output = render(5, detector("threshold=5"));
        assertFalse(output.contains("last seen"), output);
    }

    @Test
    void aDumpCanReRenderAtADifferentThreshold() {
        record(new SqlTemplate("SELECT mid FROM t WHERE id = ?", 3, false), 9, null);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new Report(5, detector("threshold=5")).print(new PrintStream(buffer, true, StandardCharsets.UTF_8), 20);
        String output = buffer.toString(StandardCharsets.UTF_8);
        assertFalse(output.contains("9 executions in one connection lease"),
                "nine executions is below a threshold of twenty:\n" + output);
    }

    private static Detector detector(String options) {
        return new Detector(EchoConfig.parse(options));
    }

    private static void record(SqlTemplate template, int executions, CallSite site) {
        Lease lease = new Lease();
        Echoes echoes = lease.record(template, executions);
        echoes.site(site);
        Ledger.record(lease);
    }

    private static String render(int threshold, Detector detector) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new Report(threshold, detector).print(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
