package exerelin.world.industry;

import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class Waystation extends IndustryClassGen {

	public Waystation() {
		super(Industries.WAYSTATION);
	}
	
	@Override
	public float getSpecialWeight(ProcGenEntity entity) {
		float weight = 2 - entity.market.getAccessibilityMod().computeEffective(0);
		if (weight < 0) weight = 0.01f;
		return weight * 2;
	}
}
