package com.agui.langchain4j.tool;

import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.function.Function;

public record ExecutableTool(
    ToolSpecification specification,
    Function<String, String> executor
) {
    public String name() {
        return specification.name();
    }
}
