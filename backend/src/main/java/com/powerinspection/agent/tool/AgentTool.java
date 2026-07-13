package com.powerinspection.agent.tool;

public interface AgentTool<I, O> {
  AgentToolDescriptor descriptor();

  AgentToolResult<O> execute(I input, AgentToolExecutionContext context);
}
