package net.ximatai.muyun.workflow.ability;

import net.ximatai.muyun.ability.curd.std.ISelectAbility;

import java.util.Map;

/**
 * 工作流能力接口
 * <p>
 * 业务模块实现此接口即可拥有工作流能力
 * </p>
 */
public interface IWorkflowAbility extends ISelectAbility {

    /**
     * 获取工作流定义编码
     * <p>
     * 业务模块需要返回对应的工作流编码
     * </p>
     *
     * @return 工作流定义的编码
     */
    String getWorkflowCode();

    /**
     * 提交审批前的回调
     * <p>
     * 业务模块可以在提交前做数据校验、状态检查等
     * </p>
     *
     * @param id 业务单据ID
     * @param body 请求体
     */
    default void beforeSubmitWorkflow(String id, Map body) {
        // 默认实现为空，业务模块可选实现
    }

    /**
     * 审批通过后的回调
     * <p>
     * 业务模块在审批通过后执行相应的业务逻辑
     * 例如：更新业务单据状态为"已审批"、发布公告等
     * </p>
     *
     * @param id 业务单据ID
     */
    default void afterWorkflowApproved(String id) {
        // 默认实现为空，业务模块可选实现
    }

    /**
     * 审批驳回后的回调
     * <p>
     * 业务模块在审批驳回后执行相应的业务逻辑
     * 例如：更新业务单据状态为"已驳回"
     * </p>
     *
     * @param id 业务单据ID
     */
    default void afterWorkflowRejected(String id) {
        // 默认实现为空，业务模块可选实现
    }

    /**
     * 获取流程变量
     * <p>
     * 从业务数据中提取需要在流程中使用的变量
     * 这些变量可以用于流程条件判断、动态办理人分配等
     * </p>
     *
     * @param id 业务单据ID
     * @return 流程变量Map
     */
    default Map<String, Object> getWorkflowVariables(String id) {
        Map data = this.view(id);
        // 默认实现：返回业务数据本身
        // 业务模块可以重写此方法，提取特定字段作为流程变量
        return data;
    }

    /**
     * 流程启动后的回调
     * <p>
     * 业务模块可以在流程启动后执行相应的业务逻辑
     * 例如：更新业务单据状态为"审批中"
     * </p>
     *
     * @param id 业务单据ID
     * @param instanceId 流程实例ID
     */
    default void afterWorkflowStarted(String id, String instanceId) {
        // 默认实现为空，业务模块可选实现
    }

    /**
     * 流程完成后的回调
     * <p>
     * 无论审批通过还是驳回，流程结束时都会调用此方法
     * </p>
     *
     * @param id 业务单据ID
     * @param instanceId 流程实例ID
     * @param status 流程最终状态（completed/terminated）
     */
    default void afterWorkflowCompleted(String id, String instanceId, String status) {
        // 默认实现为空，业务模块可选实现
    }

    /**
     * 获取流程实例ID
     * <p>
     * 从业务数据中获取关联的流程实例ID
     * 如果业务模块在表中存储了流程实例ID，可以重写此方法
     * </p>
     *
     * @param id 业务单据ID
     * @return 流程实例ID
     */
    default String getWorkflowInstanceId(String id) {
        Map data = this.view(id);
        return (String) data.get("id_at_workflow_instance");
    }
}
