package exerelin.world.industry;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class FuelProduction extends IndustryClassGen {

	public FuelProduction() {
		super(Industries.FUELPROD);
	}

	@Override
	public float getPriority(ProcGenEntity entity) {
		MarketAPI market = entity.market;
		
		float priority = 25 + market.getSize() * 5;
				
		for (MarketConditionAPI cond : market.getConditions())
		{
			switch (cond.getId())
			{
				case Conditions.VOLATILES_TRACE:
					priority += 20;
					break;
				case Conditions.VOLATILES_DIFFUSE:
					priority += 50;
					break;
				case Conditions.VOLATILES_ABUNDANT:
					priority += 75;
					break;
				case Conditions.VOLATILES_PLENTIFUL:
					priority += 100;
					break;
			}
		}
		
		// bad for high hazard worlds
		priority += (175 - market.getHazardValue()) * 5;
		
		// prefer to not be on same planet as heavy industry
		if (HeavyIndustry.hasHeavyIndustry(market))
			priority -= 350;
		
		return priority;
	}
}
