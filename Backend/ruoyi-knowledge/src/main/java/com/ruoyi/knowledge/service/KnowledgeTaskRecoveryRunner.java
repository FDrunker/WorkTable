package com.ruoyi.knowledge.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用重启后将中断的运行中任务标记为失败，允许用户手动重试。
 */
@Component
public class KnowledgeTaskRecoveryRunner implements ApplicationRunner
{
    private final KnowledgeTaskService taskService;

    public KnowledgeTaskRecoveryRunner(KnowledgeTaskService taskService)
    {
        this.taskService = taskService;
    }

    @Override
    public void run(ApplicationArguments args)
    {
        taskService.recoverInterruptedTasks();
    }
}
