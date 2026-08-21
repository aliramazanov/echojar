package shop;

public final class Servlets {

    public interface Chain {
        void proceed();
    }

    private Servlets() {
    }

    public static void request(LabServlet servlet) {
        servlet.service(null, null);
    }

    public static void filteredRequest(LabServlet servlet) {
        new LabFilter(() -> servlet.service(null, null)).doFilter(null, null, null);
    }
}
