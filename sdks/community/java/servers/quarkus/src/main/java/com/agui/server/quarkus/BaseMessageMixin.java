package com.agui.server.quarkus;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import com.agui.core.message.UserMessage;
import com.agui.core.message.AssistantMessage;
import com.agui.core.message.SystemMessage;
import com.agui.core.message.ToolMessage;
import com.agui.core.message.DeveloperMessage;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "role",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = UserMessage.class, name = "USER"),
        @JsonSubTypes.Type(value = AssistantMessage.class, name = "ASSISTANT"),
        @JsonSubTypes.Type(value = SystemMessage.class, name = "SYSTEM"),
        @JsonSubTypes.Type(value = ToolMessage.class, name = "TOOL"),
        @JsonSubTypes.Type(value = DeveloperMessage.class, name = "DEVELOPER")
})
public interface BaseMessageMixin {
    // This interface remains empty. It just acts as an annotation mirror.
}