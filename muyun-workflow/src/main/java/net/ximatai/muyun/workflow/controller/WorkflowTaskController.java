package net.ximatai.muyun.workflow.controller;

import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import net.ximatai.muyun.ability.IReferableAbility;
import net.ximatai.muyun.ability.IReferenceAbility;
import net.ximatai.muyun.ability.curd.std.IQueryAbility;
import net.ximatai.muyun.base.BaseBusinessTable;
import net.ximatai.muyun.core.db.PresetColumn;
import net.ximatai.muyun.core.exception.MuYunException;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import net.ximatai.muyun.model.PageResult;
import net.ximatai.muyun.model.QueryItem;
import net.ximatai.muyun.model.ReferenceInfo;
import net.ximatai.muyun.platform.ScaffoldForPlatform;
import net.ximatai.muyun.platform.ability.IModuleRegisterAbility;
import net.ximatai.muyun.platform.controller.DictCategoryController;
import net.ximatai.muyun.platform.controller.DictController;
import net.ximatai.muyun.platform.controller.ModuleController;
import net.ximatai.muyun.platform.controller.UserInfoController;
import net.ximatai.muyun.platform.model.Dict;
import net.ximatai.muyun.platform.model.DictCategory;
import net.ximatai.muyun.platform.model.ModuleAction;
import net.ximatai.muyun.platform.model.ModuleConfig;
import net.ximatai.muyun.workflow.engine.WorkflowEngine;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

import static net.ximatai.muyun.platform.PlatformConst.BASE_PATH;
import static net.ximatai.muyun.workflow.controller.WorkflowTaskController.MODULE_ALIAS;

@Startup
@Tag(description = "工作流任务")
@Path(BASE_PATH + "/" + MODULE_ALIAS)
public class WorkflowTaskController extends ScaffoldForPlatform
    implements IReferableAbility, IReferenceAbility, IModuleRegisterAbility, IQueryAbility {

    public final static String MODULE_ALIAS = "workflow_task";

    @Inject
    WorkflowInstanceController workflowInstanceController;

    @Inject
    WorkflowNodeController workflowNodeController;

    @Inject
    UserInfoController userInfoController;

    @Inject
    DictController dictController;

    @Inject
    DictCategoryController dictCategoryController;

    @Inject
    ModuleController moduleController;

    @Inject
    WorkflowEngine workflowEngine;

    @Override
    public String getMainTable() {
        return "workflow_task";
    }

    @Override
    protected void afterInit() {
        super.afterInit();

        // 注册任务状态字典
        dictCategoryController.putDictCategory(
            new DictCategory("dict_task_status", "platform_dir", "任务状态", 0).setDictList(
                new Dict("pending", "待办"),
                new Dict("completed", "已完成"),
                new Dict("rejected", "已驳回"),
                new Dict("transferred", "已转办"),
                new Dict("cancelled", "已取消")
            ), false);

        // 注册实例状态字典
        dictCategoryController.putDictCategory(
            new DictCategory("dict_instance_status", "platform_dir", "实例状态", 0).setDictList(
                new Dict("running", "运行中"),
                new Dict("completed", "已完成"),
                new Dict("terminated", "已终止"),
                new Dict("suspended", "已暂停")
            ), false);
    }

    @Override
    public void fitOut(TableWrapper wrapper) {
        wrapper
            .setComment("工作流任务")
            .setPrimaryKey(PresetColumn.ID_POSTGRES_UUID)
            .setInherit(BaseBusinessTable.TABLE)
            .addColumn("id_at_workflow_instance", "流程实例ID")
            .addColumn("id_at_workflow_node", "节点ID")
            .addColumn("v_name", "任务名称")
            .addColumn("dict_task_status", "任务状态", "'pending'")
            .addColumn("id_assignee", "当前办理人ID")
            .addColumn("id_prev_assignee", "上一办理人ID")
            .addColumn("t_claim_time", "签收时间")
            .addColumn("t_complete_time", "完成时间")
            .addColumn("v_comment", "审批意见")
            .addColumn("v_action", "操作")
            .addColumn("j_form_data", "表单数据")
            .addIndex("id_at_workflow_instance", false)
            .addIndex("id_assignee", false)
            .addIndex("dict_task_status", false);
    }

    @Override
    public List<ReferenceInfo> getReferenceList() {
        return List.of(
            workflowInstanceController.toReferenceInfo("id_at_workflow_instance")
                .add("v_business_key", "v_business_key"),
            workflowNodeController.toReferenceInfo("id_at_workflow_node")
                .add("v_name", "v_node_name"),
            userInfoController.toReferenceInfo("id_assignee")
                .add("v_name", "v_assignee_name"),
            userInfoController.toReferenceInfo("id_prev_assignee")
                .add("v_name", "v_prev_assignee_name"),
            dictController.toReferenceInfo("dict_task_status")
                .add("v_name", "v_status_name")
        );
    }

    @GET
    @Path("/todo")
    @Operation(summary = "我的待办")
    public PageResult<Map> getTodoList(
        @QueryParam("page") Integer page,
        @QueryParam("size") Integer size
    ) {
        String userId = getUser().getId();

        Map<String, Object> filter = Map.of(
            "id_assignee", userId,
            "dict_task_status", "pending"
        );
        return this.view(page, size != null ? Long.valueOf(size) : null, null, null, filter, this.queryGroup());
    }

    @GET
    @Path("/done")
    @Operation(summary = "我的已办")
    public PageResult<Map> getDoneList(
        @QueryParam("page") Integer page,
        @QueryParam("size") Integer size
    ) {
        String userId = getUser().getId();

        // 使用 av_ 前缀表示 ANY 操作符查询（数组包含）
        Map<String, Object> filter = Map.of(
            "id_assignee", userId,
            "av_dict_task_status", new String[]{"completed", "rejected"}
        );
        return this.view(page, size != null ? Long.valueOf(size) : null, null, null, filter, this.queryGroup());
    }

    @POST
    @Path("/complete/{id}")
    @Operation(summary = "完成任务")
    public void complete(@PathParam("id") String taskId, Map<String, Object> body) {
        // 校验任务权限
        Map<String, ?> task = view(taskId);
        String assigneeId = (String) task.get("id_assignee");
        String userId = getUser().getId();

        if (!userId.equals(assigneeId)) {
            throw new MuYunException("无权操作此任务");
        }

        if (!"pending".equals(task.get("dict_task_status"))) {
            throw new MuYunException("任务状态不正确");
        }

        String action = (String) body.get("v_action");
        String comment = (String) body.get("v_comment");
        Map<String, Object> formData = (Map<String, Object>) body.get("j_form_data");

        workflowEngine.completeTask(taskId, action, comment, formData);
    }

    @POST
    @Path("/transfer/{id}")
    @Operation(summary = "转办任务")
    public void transfer(
        @PathParam("id") String taskId,
        @QueryParam("toUser") String toUserId
    ) {
        Map<String, ?> task = view(taskId);
        String assigneeId = (String) task.get("id_assignee");
        String userId = getUser().getId();

        if (!userId.equals(assigneeId)) {
            throw new MuYunException("无权操作此任务");
        }

        if (!"pending".equals(task.get("dict_task_status"))) {
            throw new MuYunException("任务状态不正确");
        }

        // 更新任务办理人
        update(taskId, Map.of(
            "id_prev_assignee", userId,
            "id_assignee", toUserId,
            "dict_task_status", "transferred"
        ));

        // TODO: 发送消息通知新办理人
    }

    @POST
    @Path("/claim/{id}")
    @Operation(summary = "签收任务")
    public void claim(@PathParam("id") String taskId) {
        Map<String, ?> task = view(taskId);
        String assigneeId = (String) task.get("id_assignee");
        String userId = getUser().getId();

        if (!userId.equals(assigneeId)) {
            throw new MuYunException("无权操作此任务");
        }

        update(taskId, Map.of(
            "t_claim_time", java.time.LocalDateTime.now()
        ));
    }

    @Override
    public ModuleConfig getModuleConfig() {
        return ModuleConfig.ofName("工作流任务")
            .setAlias(MODULE_ALIAS)
            .setTable(getMainTable())
            .setUrl("platform/workflow/task/index")
            .addAction(new ModuleAction("complete", "完成任务", ModuleAction.TypeLike.UPDATE))
            .addAction(new ModuleAction("transfer", "转办", ModuleAction.TypeLike.UPDATE))
            .addAction(new ModuleAction("claim", "签收", ModuleAction.TypeLike.UPDATE));
    }

    @Override
    public ModuleController getModuleController() {
        return moduleController;
    }

    @Override
    public List<QueryItem> queryItemList() {
        return List.of(
            QueryItem.of("v_name"),
            QueryItem.of("dict_task_status")
        );
    }
}
