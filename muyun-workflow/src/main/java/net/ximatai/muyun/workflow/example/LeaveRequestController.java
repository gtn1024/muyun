package net.ximatai.muyun.workflow.example;

import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import net.ximatai.muyun.ability.IReferableAbility;
import net.ximatai.muyun.base.BaseBusinessTable;
import net.ximatai.muyun.core.db.PresetColumn;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import net.ximatai.muyun.platform.ScaffoldForPlatform;
import net.ximatai.muyun.platform.ability.IModuleRegisterAbility;
import net.ximatai.muyun.platform.controller.ModuleController;
import net.ximatai.muyun.platform.model.ModuleAction;
import net.ximatai.muyun.platform.model.ModuleConfig;
import net.ximatai.muyun.workflow.ability.IWorkflowAbility;
import net.ximatai.muyun.workflow.engine.WorkflowEngine;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.HashMap;
import java.util.Map;

import static net.ximatai.muyun.platform.PlatformConst.BASE_PATH;

/**
 * 请假申请示例
 * <p>
 * 演示业务模块如何集成工作流能力
 * </p>
 */
@Startup
@Tag(description = "请假申请")
@Path(BASE_PATH + "/leave_request")
public class LeaveRequestController extends ScaffoldForPlatform
    implements IModuleRegisterAbility, IReferableAbility, IWorkflowAbility {

    public final static String MODULE_ALIAS = "leave_request";

    @Inject
    ModuleController moduleController;

    @Inject
    WorkflowEngine workflowEngine;

    @Override
    public String getMainTable() {
        return "example_leave_request";
    }

    @Override
    public void fitOut(TableWrapper wrapper) {
        wrapper
            .setComment("请假申请")
            .setPrimaryKey(PresetColumn.ID_POSTGRES_UUID)
            .setInherit(BaseBusinessTable.TABLE)
            .addColumn("v_title", "标题")
            .addColumn("d_start_date", "开始日期")
            .addColumn("d_end_date", "结束日期")
            .addColumn("i_days", "请假天数")
            .addColumn("v_reason", "请假原因")
            .addColumn("dict_leave_type", "请假类型")
            .addColumn("dict_status", "状态", "'draft'")
            .addColumn("id_at_workflow_instance", "流程实例ID");
    }

    @Override
    public String getWorkflowCode() {
        return "leave_approval"; // 对应的工作流定义编码
    }

    @POST
    @Path("/submit/{id}")
    @Operation(summary = "提交审批")
    public String submitForApproval(@PathParam("id") String id) {
        // 1. 业务校验
        beforeSubmitWorkflow(id, null);

        // 2. 获取流程变量
        Map<String, Object> variables = getWorkflowVariables(id);

        // 3. 启动工作流
        String instanceId = workflowEngine.startProcess(
            getWorkflowCode(),           // 流程定义编码
            id,                          // 业务单据ID
            getModuleConfig().getAlias(), // 模块别名
            getUser().getId(),           // 发起人ID
            variables                    // 流程变量
        );

        // 4. 更新业务状态
        this.update(id, Map.of(
            "dict_status", "approving",
            "id_at_workflow_instance", instanceId
        ));

        // 5. 流程启动后回调
        afterWorkflowStarted(id, instanceId);

        return instanceId;
    }

    @Override
    public void beforeSubmitWorkflow(String id, Map body) {
        // 业务校验逻辑
        Map<String, ?> data = view(id);

        if (data.get("d_start_date") == null || data.get("d_end_date") == null) {
            throw new RuntimeException("请假日期不能为空");
        }

        if (data.get("v_reason") == null || ((String) data.get("v_reason")).isEmpty()) {
            throw new RuntimeException("请假原因不能为空");
        }
    }

    @Override
    public Map<String, Object> getWorkflowVariables(String id) {
        Map<String, ?> data = view(id);

        // 提取业务相关变量供流程使用
        Map<String, Object> variables = new HashMap<>();
        variables.put("starterId", getUser().getId());
        variables.put("days", data.get("i_days"));
        variables.put("leaveType", data.get("dict_leave_type"));
        variables.put("applicantName", getUser().getName());
        variables.put("businessKey", id);

        // 根据请假天数决定审批级别
        Integer days = (Integer) data.get("i_days");
        if (days != null) {
            if (days <= 3) {
                variables.put("approvalLevel", "manager");
            } else if (days <= 7) {
                variables.put("approvalLevel", "director");
            } else {
                variables.put("approvalLevel", "ceo");
            }
        }

        return variables;
    }

    @Override
    public void afterWorkflowStarted(String id, String instanceId) {
        // 流程启动后的业务逻辑
        System.out.println("请假申请 [" + id + "] 已提交审批，流程实例: " + instanceId);
    }

    @Override
    public void afterWorkflowApproved(String id) {
        // 审批通过后的业务逻辑
        this.update(id, Map.of("dict_status", "approved"));
        System.out.println("请假申请 [" + id + "] 已审批通过");

        // 可以在这里发送通知、更新考勤等
    }

    @Override
    public void afterWorkflowRejected(String id) {
        // 审批驳回后的业务逻辑
        this.update(id, Map.of("dict_status", "rejected"));
        System.out.println("请假申请 [" + id + "] 已被驳回");
    }

    @Override
    public void afterWorkflowCompleted(String id, String instanceId, String status) {
        // 流程完成后的业务逻辑（无论通过还是驳回）
        System.out.println("请假申请 [" + id + "] 流程已完成，状态: " + status);
    }

    @Override
    public ModuleConfig getModuleConfig() {
        return ModuleConfig.ofName("请假申请")
            .setAlias(MODULE_ALIAS)
            .setTable(getMainTable())
            .setUrl("platform/leave/index")
            .addAction(new ModuleAction("submit", "提交审批", ModuleAction.TypeLike.UPDATE));
    }

    @Override
    public ModuleController getModuleController() {
        return moduleController;
    }
}
