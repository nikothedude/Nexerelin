package exerelin.campaign.entities;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.SensorArrayEntityPlugin;
import static com.fs.starfarer.api.impl.campaign.SensorArrayEntityPlugin.SENSOR_BONUS;
import static com.fs.starfarer.api.impl.campaign.SensorArrayEntityPlugin.SENSOR_BONUS_MAKESHIFT;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;

public class Nex_SensorArrayEntityPlugin extends SensorArrayEntityPlugin {
	
	@Override
	public void advance(float amount) {
		if (entity.getContainingLocation() == null || entity.isInHyperspace()) return;
		
		String id = getModId();
		for (CampaignFleetAPI fleet : entity.getContainingLocation().getFleets()) {
			if (fleet.isInHyperspaceTransition()) continue;
			
			if (canReceiveBonus(fleet)) {
				
				String desc = Misc.ucFirst(entity.getCustomEntitySpec().getDefaultName().toLowerCase());
				float bonus = SENSOR_BONUS;
				if (isMakeshift()) {
					bonus = SENSOR_BONUS_MAKESHIFT;
				}
				
				MutableStat.StatMod curr = fleet.getStats().getSensorRangeMod().getFlatBonus(id);
				if (curr == null || curr.value <= bonus) {
					fleet.getStats().addTemporaryModFlat(0.1f, id,
							desc, bonus, 
							fleet.getStats().getSensorRangeMod());
				}
			}
		}
	}
	
	public boolean canReceiveBonus(CampaignFleetAPI fleet) {
		if (fleet.getFaction() == entity.getFaction())
			return true;
		if (fleet.getFaction().isPlayerFaction()) {
			if (isHacked()) return true;
			if (entity.getFaction() == Misc.getCommissionFaction()) return true;
		}
		if (AllianceManager.areFactionsAllied(fleet.getFaction().getId(), entity.getFaction().getId()))
			return true;
		
		return false;
	}
}
