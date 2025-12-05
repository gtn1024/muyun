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
public class WorkflowTransitionController extends ScaffoldForPlatform implements IChildAbility, IReferableAbility, IQueryAbility {

    @Override
    public String getMainTable() {
        return "workflow_transition";
    }

    @Override
    public void fitOut(TableWrapper wrapper) {
        wrapper
            .setComment("工作流流转")
            .setPrimaryKey(PresetColumn.ID_POSTGRES_UUID)
            .addColumn("id_at_workflow_definition", "流程定义ID")
            .addColumn("id_from_node", "源节点ID")
            .addColumn("id_to_node", "目标节点ID")
            .addColumn("v_name", "流转名称")
            .addColumn("v_condition", "流转条件")
            .addColumn("i_order", "排序", "0")
            .addIndex("id_at_workflow_definition", false);
    }

    @Override
    public List<QueryItem> queryItemList() {
        return List.of(
            QueryItem.of("id_at_workflow_definition"),
            QueryItem.of("id_from_node")
        );
    }
}
