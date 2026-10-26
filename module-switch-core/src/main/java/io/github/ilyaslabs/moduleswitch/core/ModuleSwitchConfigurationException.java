package io.github.ilyaslabs.moduleswitch.core;

import java.util.List;

/**
 * Thrown at startup when the module configuration or structure is invalid. Lists every problem
 * found, so all of them can be fixed in one go.
 */
public class ModuleSwitchConfigurationException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final List<String> problems;

    public ModuleSwitchConfigurationException(List<String> problems) {
        super(format(problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> getProblems() {
        return this.problems;
    }

    private static String format(List<String> problems) {
        if (problems.size() == 1) {
            return "Module-Switch: " + problems.getFirst();
        }
        StringBuilder message = new StringBuilder("Module-Switch found ").append(problems.size()).append(" problems:");
        for (String problem : problems) {
            message.append(System.lineSeparator()).append(" - ").append(problem);
        }
        return message.toString();
    }
}
