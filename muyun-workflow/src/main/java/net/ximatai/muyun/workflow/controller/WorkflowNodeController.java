package net.ximatai.muyun.workflow.controller;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import net.ximatai.muyun.ability.IChildAbility;
import net.ximatai.muyun.ability.IReferableAbility;
import net.ximatai.muyun.ability.curd.std.IQueryAbility;
import net.ximatai.muyun.core.db.PresetColumn;
import net.ximatai.muyun.database.core.builder.TableWrapper;
import net.ximatai.muyun.model.QueryItem;
import net.ximatai.muyun.platform.ScaffoldForPlatform;

import java.util.List;

@Startup
@ApplicationScoped
public class WorkflowNodeController extends ScaffoldForPlatform implements IChildAbility, IReferableAbility, IQueryAbility {

    @Override
    public String getMainTable() {
        return "workflow_node";
    }

    @Override
    public void fitOut(TableWrapper wrapper) {
        wrapper
            .setComment("工作流节点")
            .setPrimaryKey(PresetColumn.ID_POSTGRES_UUID)
            .addColumn("id_at_workflow_definition", "流程定义ID")
            .addColumn("v_name", "节点名称")
            .addColumn("v_code", "节点编码")
            .addColumn("dict_node_type", "节点类型")
            .addColumn("dict_assignee_type", "办理人类型")
            .addColumn("ids_assignee", "办理人ID列表")
            .addColumn("j_form_config", "表单配置")
            .addColumn("j_button_config", "按钮配置")
            .addColumn("i_order", "排序", "0")
            .addIndex("id_at_workflow_definition", false);
    }

    @Override
    public List<QueryItem> queryItemList() {
        return List.of(
            QueryItem.of("id_at_workflow_definition"),
            QueryItem.of("dict_node_type")
        );
    }
}
