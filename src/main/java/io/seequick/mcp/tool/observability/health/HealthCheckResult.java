package io.seequick.mcp.tool.observability.health;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulator for health check results across multiple checkers.
 */
public class HealthCheckResult {

    private final StringBuilder output;
    private final List<String> sections;
    private int totalIssues;
    private int warnings;

    public HealthCheckResult() {
        this.output = new StringBuilder();
        this.sections = new ArrayList<>();
        this.totalIssues = 0;
        this.warnings = 0;
    }

    /**
     * Starts a new section in the output.
     */
    public HealthCheckResult startSection(String title) {
        sections.add(title);
        output.append(title).append("\n");
        output.append("\u2500".repeat(40)).append("\n");
        return this;
    }

    /**
     * Appends text to the output.
     */
    public HealthCheckResult append(String text) {
        output.append(text);
        return this;
    }

    /**
     * Appends a new line.
     */
    public HealthCheckResult newLine() {
        output.append("\n");
        return this;
    }

    /**
     * Increments the issue count.
     */
    public void addIssue() {
        totalIssues++;
    }

    /**
     * Increments the warning count.
     */
    public void addWarning() {
        warnings++;
    }

    public int getTotalIssues() {
        return totalIssues;
    }

    public int getWarnings() {
        return warnings;
    }

    /**
     * Formats the complete health check report.
     */
    public String format() {
        StringBuilder result = new StringBuilder();
        result.append("Strimzi Health Check Report\n");
        result.append("\u2550".repeat(60)).append("\n\n");
        result.append(output);
        result.append("\u2550".repeat(60)).append("\n");
        result.append("SUMMARY\n");

        if (totalIssues == 0 && warnings == 0) {
            result.append("\u2713 All resources are healthy!\n");
        } else {
            if (totalIssues > 0) {
                result.append("\u2717 Critical issues: ").append(totalIssues).append("\n");
            }
            if (warnings > 0) {
                result.append("\u26A0 Warnings: ").append(warnings).append("\n");
            }
            result.append("\nUse describe_* tools to investigate specific resources.\n");
        }

        return result.toString();
    }
}
