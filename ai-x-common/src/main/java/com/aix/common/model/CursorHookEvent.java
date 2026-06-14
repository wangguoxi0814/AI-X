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

    UNKNOWN(0, null, "未知事件"),
    SESSION_START(1, "sessionStart", "会话开始"),
    SESSION_END(2, "sessionEnd", "会话结束"),
    PRE_TOOL_USE(3, "preToolUse", "工具调用前"),
    POST_TOOL_USE(4, "postToolUse", "工具调用后"),
    POST_TOOL_USE_FAILURE(5, "postToolUseFailure", "工具调用失败"),
    SUBAGENT_START(6, "subagentStart", "子 Agent 启动"),
    SUBAGENT_STOP(7, "subagentStop", "子 Agent 结束"),
    BEFORE_SHELL_EXECUTION(8, "beforeShellExecution", "Shell 命令执行前"),
    AFTER_SHELL_EXECUTION(9, "afterShellExecution", "Shell 命令执行后"),
    BEFORE_MCP_EXECUTION(10, "beforeMCPExecution", "MCP 工具执行前"),
    AFTER_MCP_EXECUTION(11, "afterMCPExecution", "MCP 工具执行后"),
    BEFORE_READ_FILE(12, "beforeReadFile", "读取文件前"),
    AFTER_FILE_EDIT(13, "afterFileEdit", "文件编辑后"),
    BEFORE_SUBMIT_PROMPT(14, "beforeSubmitPrompt", "用户提交 Prompt 前"),
    PRE_COMPACT(15, "preCompact", "上下文压缩前"),
    STOP(16, "stop", "Agent 任务结束"),
    AFTER_AGENT_RESPONSE(17, "afterAgentResponse", "Agent 回复完成后"),
    AFTER_AGENT_THOUGHT(18, "afterAgentThought", "Agent 思考块完成后"),
    BEFORE_TAB_FILE_READ(19, "beforeTabFileRead", "Tab 读取文件前"),
    AFTER_TAB_FILE_EDIT(20, "afterTabFileEdit", "Tab 编辑文件后"),
    WORKSPACE_OPEN(21, "workspaceOpen", "工作区打开");

    private static final Map<Integer, CursorHookEvent> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(CursorHookEvent::getCode, Function.identity()));

    private static final Map<String, CursorHookEvent> BY_HOOK_NAME = Arrays.stream(values())
            .filter(event -> event.hookName != null)
            .collect(Collectors.toMap(CursorHookEvent::getHookName, Function.identity()));

    private final int code;
    private final String hookName;
    private final String description;

    CursorHookEvent(int code, String hookName, String description) {
        this.code = code;
        this.hookName = hookName;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getHookName() {
        return hookName;
    }

    public String getDescription() {
        return description;
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
