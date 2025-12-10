package net.ximatai.muyun.workflow.controller;

import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import net.ximatai.muyun.ability.IReferableAbility;
import net.ximatai.muyun.ability.IReferenceAbility;
import net.ximatai.muyun.ability.curd.std.IQueryAbility;
import net.ximatai.muyun.core.db.PresetColumn;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import net.ximatai.muyun.model.QueryItem;
import net.ximatai.muyun.model.ReferenceInfo;
import net.ximatai.muyun.platform.ScaffoldForPlatform;
import net.ximatai.muyun.platform.ability.IModuleRegisterAbility;
import net.ximatai.muyun.platform.controller.ModuleController;
import net.ximatai.muyun.platform.controller.UserInfoController;
import net.ximatai.muyun.platform.model.ModuleConfig;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

import static net.ximatai.muyun.platform.PlatformConst.BASE_PATH;
import static net.ximatai.muyun.workflow.controller.WorkflowHistoryController.MODULE_ALIAS;

@Startup
@Tag(description = "工作流历史")
@Path(BASE_PATH + "/" + MODULE_ALIAS)
public class WorkflowHistoryController extends ScaffoldForPlatform
    implements IReferableAbility, IReferenceAbility, IQueryAbility, IModuleRegisterAbility {

    public final static String MODULE_ALIAS = "workflow_history";

    @Inject
    WorkflowInstanceController workflowInstanceController;

    @Inject
    WorkflowTaskController workflowTaskController;

    @Inject
    UserInfoController userInfoController;

    @Inject
    ModuleController moduleController;

    @Override
    public String getMainTable() {
        return "workflow_history";
    }

    @Override
    public void fitOut(TableWrapper wrapper) {
        wrapper
            .setComment("工作流历史")
            .setPrimaryKey(PresetColumn.ID_POSTGRES_UUID)
            .addColumn("id_at_workflow_instance", "流程实例ID")
            .addColumn("id_at_workflow_task", "任务ID")
            .addColumn("v_action_type", "操作类型")
            .addColumn("id_operator", "操作人ID")
            .addColumn("t_operate_time", "操作时间")
            .addColumn("v_comment", "意见")
            .addColumn("j_snapshot", "数据快照")
            .addIndex("id_at_workflow_instance", false)
            .addIndex("t_operate_time", false);
    }

    @Override
    public List<QueryItem> queryItemList() {
        return List.of(
            QueryItem.of("id_at_workflow_instance"),
            QueryItem.of("id_at_workflow_task"),
            QueryItem.of("id_operator")
        );
    }

    @Override
    public List<ReferenceInfo> getReferenceList() {
        return List.of(
            workflowInstanceController.toReferenceInfo("id_at_workflow_instance"),
            workflowTaskController.toReferenceInfo("id_at_workflow_task")
                .add("v_name", "v_task_name"),
            userInfoController.toReferenceInfo("id_operator")
                .add("v_name", "v_operator_name")
        );
    }

    @Override
    public ModuleConfig getModuleConfig() {
        return ModuleConfig.ofName("流程历史")
            .setAlias(MODULE_ALIAS)
            .setTable(getMainTable())
            .setUrl("platform/workflow/history/index");
    }

    @Override
    public ModuleController getModuleController() {
        return moduleController;
    }
}
