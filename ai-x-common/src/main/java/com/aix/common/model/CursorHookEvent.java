package com.aix.common.model;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cursor Hooks 事件枚举，与 {@code hooks.json} 中事件名及 Hook stdin 的 {@code hook_event_name} 对齐。
 * 持久化时使用 {@link #code}（Integer），避免字符串漂移。
 */
public enum CursorHookEvent {

    UNKNOWN(0, null),
    SESSION_START(1, "sessionStart"),
    SESSION_END(2, "sessionEnd"),
    PRE_TOOL_USE(3, "preToolUse"),
    POST_TOOL_USE(4, "postToolUse"),
    POST_TOOL_USE_FAILURE(5, "postToolUseFailure"),
    SUBAGENT_START(6, "subagentStart"),
    SUBAGENT_STOP(7, "subagentStop"),
    BEFORE_SHELL_EXECUTION(8, "beforeShellExecution"),
    AFTER_SHELL_EXECUTION(9, "afterShellExecution"),
    BEFORE_MCP_EXECUTION(10, "beforeMCPExecution"),
    AFTER_MCP_EXECUTION(11, "afterMCPExecution"),
    BEFORE_READ_FILE(12, "beforeReadFile"),
    AFTER_FILE_EDIT(13, "afterFileEdit"),
    BEFORE_SUBMIT_PROMPT(14, "beforeSubmitPrompt"),
    PRE_COMPACT(15, "preCompact"),
    STOP(16, "stop"),
    AFTER_AGENT_RESPONSE(17, "afterAgentResponse"),
    AFTER_AGENT_THOUGHT(18, "afterAgentThought"),
    BEFORE_TAB_FILE_READ(19, "beforeTabFileRead"),
    AFTER_TAB_FILE_EDIT(20, "afterTabFileEdit"),
    WORKSPACE_OPEN(21, "workspaceOpen");

    private static final Map<Integer, CursorHookEvent> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(CursorHookEvent::getCode, Function.identity()));

    private static final Map<String, CursorHookEvent> BY_HOOK_NAME = Arrays.stream(values())
            .filter(event -> event.hookName != null)
            .collect(Collectors.toMap(CursorHookEvent::getHookName, Function.identity()));

    private final int code;
    private final String hookName;

    CursorHookEvent(int code, String hookName) {
        this.code = code;
        this.hookName = hookName;
    }

    public int getCode() {
        return code;
    }

    public String getHookName() {
        return hookName;
    }

    public static CursorHookEvent fromCode(Integer code) {
        if (code == null) {
            return UNKNOWN;
        }
        return BY_CODE.getOrDefault(code, UNKNOWN);
    }

    public static CursorHookEvent fromHookName(String hookName) {
        if (hookName == null || hookName.isBlank()) {
            return UNKNOWN;
        }
        return BY_HOOK_NAME.getOrDefault(hookName, UNKNOWN);
    }
}
