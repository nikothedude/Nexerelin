package exerelin.campaign.ai.action;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.StrategicDefManager;
import exerelin.campaign.ai.concern.StrategicConcern;

public interface StrategicAction {

    StrategicAI getAI();
    void setAI(StrategicAI ai);
    StrategicActionDelegate.ActionStatus getStatus();

    StrategicConcern getConcern();
    void setConcern(StrategicConcern concern);

    StrategicActionDelegate getDelegate();
    void setDelegate(StrategicActionDelegate delegate);

    /**
     * Creates the action. {@code canUse()} should already have been called beforehand to determine as much as possible
     * whether the action can actually be executed, but we may nevertheless find the action unable to proceed here.
     * @return True if successfully generated, false otherwise.
     */
    boolean generate();

    /**
     * Called after {@code generate()} to actually begin executing the action.
     */
    void init();
    /**
     * Called every half-day or so to ensure the action should still continue.
     * @return
     */
    boolean isValid();
    MutableStat getPriority();
    float getPriorityFloat();
    void updatePriority();

    void advance(float days);
    void abort();
    void end(StrategicActionDelegate.ActionStatus newStatus);
    boolean isEnded();

    String getName();
    String getIcon();
    String getId();
    void setId(String id);

    /**
     * Can the action be used, with the specified concern or in general? Called when deciding which action to take.
     * @param concern
     * @return
     */
    boolean canUse(StrategicConcern concern);
    RepLevel getMinRelToTarget(FactionAPI target);
    RepLevel getMaxRelToTarget(FactionAPI target);

    StrategicDefManager.StrategicActionDef getDef();

    void createPanel(CustomPanelAPI outer, TooltipMakerAPI tooltip);
}
