package exerelin.campaign.events;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.StringHelper;

/**
 * this handles event reporting for intel screen
 * for the market condition see AgentDestabilizeMarketEventForCondition
 */
public class AgentDestabilizeMarketEvent extends CovertOpsEventBase {

	public static Logger log = Global.getLogger(AgentDestabilizeMarketEvent.class);
	protected static final String[] ACTION_LINES;
	
	static {
		List<String> lines = new ArrayList<>();
		int numLines = 8;	// must not exceed what is inside strings.json
		for (int i=0; i<numLines; i++)
		{
			lines.add(StringHelper.getString("exerelin_agents", "destabilizeText" + (i + 1)));
		}
		ACTION_LINES = lines.toArray(new String[numLines]);
	}
	
	protected int stabilityPenalty;
	
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		stabilityPenalty = 0;
	}
	
	@Override
	public void setParam(Object param) {
		super.setParam(param);
		if (params.containsKey("stabilityPenalty"))
			stabilityPenalty = (Integer)params.get("stabilityPenalty");
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		String actionLine = (String) ExerelinUtils.getRandomArrayElement(ACTION_LINES);
		Map<String, String> map = super.getTokenReplacements();
		map.put("$stabilityPenalty", "" + stabilityPenalty);
		map.put("$actionLine", "" + actionLine);
		return map;
	}
	
	@Override
	public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<>();
		if (stabilityPenalty != 0)
			addTokensToList(result, "$stabilityPenalty");
		addTokensToList(result, "$repEffectAbs");
		addTokensToList(result, "$newRelationStr");
		
		return result.toArray(new String[0]);
	}
	
	@Override
	public Color[] getHighlightColors(String stageId) {
		Color colorRepEffect = repEffect > 0 ? Global.getSettings().getColor("textFriendColor") : Global.getSettings().getColor("textEnemyColor");
		Color colorNew = agentFaction.getRelColor(faction.getId());
		if (stabilityPenalty != 0)
			return new Color[] {Misc.getHighlightColor(), colorRepEffect, colorNew};
		else return new Color[] {colorRepEffect, colorNew};
	}
}