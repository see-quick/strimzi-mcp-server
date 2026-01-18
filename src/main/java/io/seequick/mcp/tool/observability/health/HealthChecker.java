package io.seequick.mcp.tool.observability.health;

/**
 * Interface for health checkers in the chain of responsibility.
 */
public interface HealthChecker {

    /**
     * Performs the health check and adds results to the result accumulator.
     *
     * @param context The health check context with client and filters
     * @param result  The result accumulator to add findings to
     */
    void check(HealthCheckContext context, HealthCheckResult result);

    /**
     * Returns the title for this checker's section in the report.
     */
    String getSectionTitle();
}
