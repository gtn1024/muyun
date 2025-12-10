package net.ximatai.muyun.workflow.engine;

import io.vertx.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.ximatai.muyun.core.exception.MuYunException;
import net.ximatai.muyun.platform.service.MessageCenter;
import net.ximatai.muyun.platform.model.MuYunMessage;
import net.ximatai.muyun.workflow.controller.*;

import java.time.LocalDateTime;
import java.util.*;

@ApplicationScoped
public class WorkflowEngine {

    @Inject
    WorkflowDefinitionController workflowDefinitionController;

    @Inject
    WorkflowNodeController workflowNodeController;

    @Inject
    WorkflowTransitionController workflowTransitionController;

    @Inject
    WorkflowInstanceController workflowInstanceController;

    @Inject
    WorkflowTaskController workflowTaskController;

    @Inject
    WorkflowHistoryController workflowHistoryController;

    @Inject
    MessageCenter messageCenter;

    @Inject
    EventBus eventBus;

    /**
     * 启动流程
     *
     * @param definitionId 流程定义ID
     * @param businessKey  业务单据ID
     * @param moduleId     业务模块ID
     * @param starterId    发起人ID
     * @param variables    流程变量
     * @return 流程实例ID
     */
    public String startProcess(String definitionId, String businessKey, String moduleId,
                               String starterId, Map<String, Object> variables) {
        // 1. 校验流程定义是否激活
        Map<String, ?> definition = workflowDefinitionController.view(definitionId);
        if (!"active".equals(definition.get("dict_workflow_status"))) {
            throw new MuYunException("流程未激活，无法启动");
        }

        // 2. 创建流程实例
        String instanceId = workflowInstanceController.create(Map.of(
            "id_at_workflow_definition", definitionId,
            "v_business_key", businessKey,
            "id_at_app_module", moduleId,
            "dict_instance_status", "running",
            "id_starter", starterId,
            "t_start_time", LocalDateTime.now(),
            "j_variables", variables != null ? variables : Map.of()
        ));

        // 3. 记录历史
        recordHistory(instanceId, null, "START", starterId, "发起流程", null);

        // 4. 找到开始节点
        List<Map> nodes = workflowNodeController.view(
            null, null, true, null,
            Map.of(
                "id_at_workflow_definition", definitionId,
                "dict_node_type", "start"
            )
        ).getList();

        if (nodes.isEmpty()) {
            throw new MuYunException("流程定义缺少开始节点");
        }

        Map<String, ?> startNode = nodes.get(0);
        String startNodeId = (String) startNode.get("id");

        // 5. 找到开始节点的下一节点
        List<Map<String, ?>> nextNodes = calculateNextNodes(definitionId, startNodeId, "start", variables);

        // 6. 创建下一节点的任务
        for (Map<String, ?> nextNode : nextNodes) {
            createTasksForNode(instanceId, nextNode, variables, starterId);
        }

        return instanceId;
    }

    /**
     * 完成任务
     *
     * @param taskId   任务ID
     * @param action   操作（同意/驳回/转办）
     * @param comment  审批意见
     * @param formData 表单数据
     */
    public void completeTask(String taskId, String action, String comment, Map<String, Object> formData) {
        // 1. 获取任务信息
        Map<String, ?> task = workflowTaskController.view(taskId);
        String instanceId = (String) task.get("id_at_workflow_instance");
        String nodeId = (String) task.get("id_at_workflow_node");
        String assigneeId = (String) task.get("id_assignee");

        // 2. 获取流程实例信息
        Map<String, ?> instance = workflowInstanceController.view(instanceId);
        String definitionId = (String) instance.get("id_at_workflow_definition");
        Map<String, Object> variables = (Map<String, Object>) instance.get("j_variables");
        if (variables == null) {
            variables = new HashMap<>();
        }

        // 3. 更新任务状态
        workflowTaskController.update(taskId, Map.of(
            "dict_task_status", "completed",
            "t_complete_time", LocalDateTime.now(),
            "v_action", action,
            "v_comment", comment != null ? comment : "",
            "j_form_data", formData != null ? formData : Map.of()
        ));

        // 4. 记录历史
        recordHistory(instanceId, taskId, action.toUpperCase(), assigneeId, comment, formData);

        // 5. 获取当前节点信息
        Map<String, ?> currentNode = workflowNodeController.view(nodeId);

        // 6. 计算下一节点
        List<Map<String, ?>> nextNodes = calculateNextNodes(definitionId, nodeId, action, variables);

        if (nextNodes.isEmpty()) {
            // 没有下一节点，检查是否所有任务都已完成
            checkAndCompleteInstance(instanceId);
        } else {
            // 7. 创建下一节点的任务
            for (Map<String, ?> nextNode : nextNodes) {
                String nextNodeType = (String) nextNode.get("dict_node_type");

                if ("end".equals(nextNodeType)) {
                    // 到达结束节点，完成流程实例
                    completeInstance(instanceId);
                } else {
                    createTasksForNode(instanceId, nextNode, variables, assigneeId);
                }
            }
        }
    }

    /**
     * 计算下一节点
     *
     * @param definitionId  流程定义ID
     * @param currentNodeId 当前节点ID
     * @param action        操作
     * @param variables     流程变量
     * @return 下一节点列表
     */
    private List<Map<String, ?>> calculateNextNodes(String definitionId, String currentNodeId,
                                                    String action, Map<String, Object> variables) {
        // 查询从当前节点出发的所有流转
        List<Map> transitions = workflowTransitionController.view(
            null, null, true, null,
            Map.of(
                "id_at_workflow_definition", definitionId,
                "id_from_node", currentNodeId
            )
        ).getList();

        List<Map<String, ?>> nextNodes = new ArrayList<>();

        for (Map<String, ?> transition : transitions) {
            String transitionName = (String) transition.get("v_name");
            String condition = (String) transition.get("v_condition");

            // 检查流转条件
            boolean shouldTransit = false;

            if (condition == null || condition.isEmpty()) {
                // 无条件流转
                shouldTransit = true;
            } else if (transitionName != null && transitionName.equalsIgnoreCase(action)) {
                // 根据操作名称匹配
                shouldTransit = true;
            } else {
                // TODO: 实现更复杂的条件表达式求值
                shouldTransit = evaluateCondition(condition, variables);
            }

            if (shouldTransit) {
                String toNodeId = (String) transition.get("id_to_node");
                Map<String, ?> nextNode = workflowNodeController.view(toNodeId);
                nextNodes.add(nextNode);
            }
        }

        return nextNodes;
    }

    /**
     * 为节点创建任务
     *
     * @param instanceId         流程实例ID
     * @param node               节点信息
     * @param variables          流程变量
     * @param previousAssigneeId 上一个办理人ID
     */
    private void createTasksForNode(String instanceId, Map<String, ?> node,
                                    Map<String, Object> variables, String previousAssigneeId) {
        String nodeId = (String) node.get("id");
        String nodeName = (String) node.get("v_name");
        String assigneeType = (String) node.get("dict_assignee_type");
        Object assigneeIds = node.get("ids_assignee");

        // 计算办理人列表
        List<String> assignees = calculateAssignees(assigneeType, assigneeIds, variables, previousAssigneeId);

        if (assignees.isEmpty()) {
            throw new MuYunException("无法找到节点 [" + nodeName + "] 的办理人");
        }

        // 为每个办理人创建任务
        for (String assigneeId : assignees) {
            String taskId = workflowTaskController.create(Map.of(
                "id_at_workflow_instance", instanceId,
                "id_at_workflow_node", nodeId,
                "v_name", nodeName,
                "dict_task_status", "pending",
                "id_assignee", assigneeId,
                "id_prev_assignee", previousAssigneeId
            ));

            // 发送消息通知
            notifyAssignee(assigneeId, taskId, nodeName);
        }
    }

    /**
     * 计算办理人
     *
     * @param assigneeType       办理人类型
     * @param assigneeIds        办理人ID列表
     * @param variables          流程变量
     * @param previousAssigneeId 上一个办理人ID
     * @return 办理人ID列表
     */
    private List<String> calculateAssignees(String assigneeType, Object assigneeIds,
                                            Map<String, Object> variables, String previousAssigneeId) {
        List<String> result = new ArrayList<>();

        switch (assigneeType) {
            case "user":
                // 指定人员
                if (assigneeIds instanceof List) {
                    result.addAll((List<String>) assigneeIds);
                } else if (assigneeIds instanceof String) {
                    result.add((String) assigneeIds);
                }
                break;
            case "role":
                // 角色：查询角色下的所有用户
                // TODO: 查询角色用户
                break;
            case "department":
                // 部门：查询部门下的所有用户
                // TODO: 查询部门用户
                break;
            case "starter":
                // 发起人
                String starterId = (String) variables.get("starterId");
                if (starterId != null) {
                    result.add(starterId);
                }
                break;
            case "superior":
                // 上级：查询发起人的上级
                // TODO: 查询上级用户
                break;
            case "dynamic":
                // 动态：根据变量计算
                Object dynamicAssignees = variables.get("assignees");
                if (dynamicAssignees instanceof List) {
                    result.addAll((List<String>) dynamicAssignees);
                }
                break;
            default:
                throw new MuYunException("不支持的办理人类型: " + assigneeType);
        }

        return result;
    }

    /**
     * 评估条件表达式
     */
    private boolean evaluateCondition(String condition, Map<String, Object> variables) {
        // TODO: 实现条件表达式求值引擎
        // 简单实现：支持基本的变量比较
        return true;
    }

    /**
     * 检查并完成流程实例
     */
    private void checkAndCompleteInstance(String instanceId) {
        // 查询实例下是否还有待办任务
        List<Map> pendingTasks = workflowTaskController.view(
            null, null, true, null,
            Map.of(
                "id_at_workflow_instance", instanceId,
                "dict_task_status", "pending"
            )
        ).getList();

        if (pendingTasks.isEmpty()) {
            completeInstance(instanceId);
        }
    }

    /**
     * 完成流程实例
     */
    private void completeInstance(String instanceId) {
        workflowInstanceController.update(instanceId, Map.of(
            "dict_instance_status", "completed",
            "t_end_time", LocalDateTime.now()
        ));

        // 记录历史
        Map<String, ?> instance = workflowInstanceController.view(instanceId);
        String starterId = (String) instance.get("id_starter");
        recordHistory(instanceId, null, "END", starterId, "流程完成", null);

        // TODO: 触发业务回调
    }

    /**
     * 记录历史
     */
    private void recordHistory(String instanceId, String taskId, String actionType,
                               String operatorId, String comment, Map<String, Object> snapshot) {
        workflowHistoryController.create(Map.of(
            "id_at_workflow_instance", instanceId,
            "id_at_workflow_task", taskId != null ? taskId : "",
            "v_action_type", actionType,
            "id_operator", operatorId,
            "t_operate_time", LocalDateTime.now(),
            "v_comment", comment != null ? comment : "",
            "j_snapshot", snapshot != null ? snapshot : Map.of()
        ));
    }

    /**
     * 通知办理人
     */
    private void notifyAssignee(String assigneeId, String taskId, String taskName) {
        MuYunMessage message = new MuYunMessage(
            "新的待办任务",
            "您收到一个新的待办任务：" + taskName,
            "/platform/workflow/task/todo"
        );
        messageCenter.send(assigneeId, message);
    }
}
