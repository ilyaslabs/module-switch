package io.github.ilyaslabs.moduleswitch.autoconfigure;

import io.github.ilyaslabs.moduleswitch.core.ModuleSwitchConfigurationException;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Turns {@link ModuleSwitchConfigurationException} into a readable startup report.
 */
class ModuleSwitchConfigurationFailureAnalyzer extends AbstractFailureAnalyzer<ModuleSwitchConfigurationException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, ModuleSwitchConfigurationException cause) {
        StringBuilder description = new StringBuilder("Module-Switch found ")
            .append(cause.getProblems().size())
            .append(cause.getProblems().size() == 1 ? " problem:" : " problems:");
        for (String problem : cause.getProblems()) {
            description.append(System.lineSeparator()).append(System.lineSeparator()).append(" - ").append(problem);
        }
        return new FailureAnalysis(description.toString(),
                "Fix the problems above. Each one names the module, class or property involved.", cause);
    }
}
