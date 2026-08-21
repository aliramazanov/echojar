package shop;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

public final class LabFilter implements Filter {

    private final Servlets.Chain chain;

    public LabFilter(Servlets.Chain chain) {
        this.chain = chain;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain next) {
        chain.proceed();
    }
}
