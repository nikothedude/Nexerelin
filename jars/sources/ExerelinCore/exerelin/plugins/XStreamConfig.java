package exerelin.plugins;

import exerelin.campaign.ColonyManager;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.abilities.ai.Nex_SustainedBurnAbilityAI;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.alliances.AllianceVoter;
import exerelin.campaign.intel.agents.InstigateRebellion;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.econ.RaidCondition;
import exerelin.campaign.econ.ReinforcedDefenses;
import exerelin.campaign.econ.TributeCondition;
import exerelin.campaign.entities.Nex_NavBuoyEntityPlugin;
import exerelin.campaign.entities.Nex_SensorArrayEntityPlugin;
import exerelin.campaign.intel.fleets.VengeanceFleetIntel;
import exerelin.campaign.events.covertops.SecurityAlertEvent;
import exerelin.campaign.events.SlavesSoldEvent;
import exerelin.campaign.events.WarmongerEvent;
import exerelin.campaign.fleets.MiningFleetAI;
import exerelin.campaign.fleets.MiningFleetManagerV2;
import exerelin.campaign.fleets.MiningFleetManagerV2.MiningFleetData;
import exerelin.campaign.fleets.VultureFleetAI;
import exerelin.campaign.fleets.VultureFleetManager.VultureFleetData;
import exerelin.campaign.intel.rebellion.SuppressionFleetAI;
import exerelin.campaign.intel.AllianceIntel;
import exerelin.campaign.intel.AllianceVoteIntel;
import exerelin.campaign.intel.BuyColonyIntel;
import exerelin.campaign.intel.missions.ConquestMissionIntel;
import exerelin.campaign.intel.missions.ConquestMissionManager;
import exerelin.campaign.intel.diplomacy.DiplomacyIntel;
import exerelin.campaign.intel.FactionBountyIntel;
import exerelin.campaign.intel.FactionSpawnedOrEliminatedIntel;
import exerelin.campaign.intel.InsuranceIntelV2;
import exerelin.campaign.intel.invasion.InvasionIntel;
import exerelin.campaign.intel.MarketTransferIntel;
import exerelin.campaign.intel.NexPirateActivity;
import exerelin.campaign.intel.Nex_PunitiveExpeditionIntel;
import exerelin.campaign.intel.PlayerOutpostIntel;
import exerelin.campaign.intel.RespawnBaseIntel;
import exerelin.campaign.intel.diplomacy.TributeIntel;
import exerelin.campaign.intel.agents.AgentIntel;
import exerelin.campaign.intel.agents.DestabilizeMarket;
import exerelin.campaign.intel.agents.DestroyCommodityStocks;
import exerelin.campaign.intel.agents.InfiltrateCell;
import exerelin.campaign.intel.agents.LowerRelations;
import exerelin.campaign.intel.agents.RaiseRelations;
import exerelin.campaign.intel.agents.SabotageIndustry;
import exerelin.campaign.intel.agents.Travel;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import exerelin.campaign.intel.diplomacy.DiplomacyProfileIntel;
import exerelin.campaign.intel.fleets.NexAssembleStage;
import exerelin.campaign.intel.fleets.NexOrganizeStage;
import exerelin.campaign.intel.fleets.NexReturnStage;
import exerelin.campaign.intel.fleets.NexTravelStage;
import exerelin.campaign.intel.fleets.ReliefFleetIntelAlt;
import exerelin.campaign.intel.invasion.InvActionStage;
import exerelin.campaign.intel.invasion.InvAssembleStage;
import exerelin.campaign.intel.invasion.InvOrganizeStage;
import exerelin.campaign.intel.invasion.RespawnInvasionIntel;
import exerelin.campaign.intel.missions.Nex_ProcurementMissionIntel;
import exerelin.campaign.intel.raid.BaseStrikeIntel;
import exerelin.campaign.intel.raid.NexRaidActionStage;
import exerelin.campaign.intel.raid.NexRaidAssembleStage;
import exerelin.campaign.intel.raid.NexRaidIntel;
import exerelin.campaign.intel.raid.RemnantRaidIntel;
import exerelin.campaign.intel.rebellion.RebellionCreator;
import exerelin.campaign.intel.rebellion.RebellionIntel;
import exerelin.campaign.intel.satbomb.SatBombActionStage;
import exerelin.campaign.intel.satbomb.SatBombIntel;
import exerelin.campaign.intel.specialforces.SpecialForcesAssignmentAI;
import exerelin.campaign.intel.specialforces.SpecialForcesIntel;
import exerelin.campaign.submarkets.Nex_BlackMarketPlugin;
import exerelin.campaign.submarkets.Nex_LocalResourcesSubmarketPlugin;
import exerelin.campaign.submarkets.Nex_MilitarySubmarketPlugin;
import exerelin.campaign.submarkets.Nex_OpenMarketPlugin;
import exerelin.campaign.submarkets.Nex_StoragePlugin;

public class XStreamConfig {
	
	public static void configureXStream(com.thoughtworks.xstream.XStream x)
	{
		/*
		x.alias("AllianceMngr", AllianceManager.class);
		x.alias("CovertOpsMngr", CovertOpsManager.class);
		x.alias("DiplomacyMngr", DiplomacyManager.class);
		x.alias("ExerelinCoreScript", ExerelinCoreScript.class);
		x.alias("PlayerFactionStore", PlayerFactionStore.class);
		x.alias("RevengeanceMngr", RevengeanceManager.class);
		x.alias("SectorMngr", SectorManager.class);
		
		x.alias("InvasionFltMngr", InvasionFleetManager.class);
		x.alias("ResponseFltMngr", ResponseFleetManager.class);
		x.alias("MiningFltMngr", MiningFleetManager.class);
		x.alias("ExePatrolFltMngr", ExerelinPatrolFleetManager.class);
		*/
		x.alias("ColonyMngr", ColonyManager.class);
		x.alias("ColonyMngrQueuedInd", ColonyManager.QueuedIndustry.class);
		
		x.alias("MiningFltAI", MiningFleetAI.class);
		x.alias("MiningFltData", MiningFleetManagerV2.MiningFleetData.class);
		x.alias("SuppressFltAI", SuppressionFleetAI.class);
		x.alias("VengFltIntl", VengeanceFleetIntel.class);
		
		x.alias("DiploIntl", DiplomacyIntel.class);
		x.alias("DiploPrflIntl", DiplomacyProfileIntel.class);
		x.alias("MktTrnsfrIntl", MarketTransferIntel.class);
		x.alias("FactionChngIntl", FactionSpawnedOrEliminatedIntel.class);
		
		// submarkets
		x.alias("NexOpnMkt", Nex_OpenMarketPlugin.class);
		x.alias("NexMilSubmkt", Nex_MilitarySubmarketPlugin.class);
		x.alias("NexBlackMkt", Nex_BlackMarketPlugin.class);
		x.alias("NexLclRsrcsSubmkt", Nex_LocalResourcesSubmarketPlugin.class);
		x.alias("NexStoreSubmkt", Nex_StoragePlugin.class);
		
		// events
		// most of these will be deleted eventually
		x.alias("InstgtRbl", InstigateRebellion.class);
		x.alias("RebelEvntCreator", RebellionCreator.class);
		x.alias("SecurityAlertEvnt", SecurityAlertEvent.class);
		x.alias("SlavesSoldEvnt", SlavesSoldEvent.class);
		x.alias("WarmongerEvnt", WarmongerEvent.class);
		
		// intel
		x.alias("NexRaidCond", RaidCondition.class);
		x.alias("NexPunExIntl", Nex_PunitiveExpeditionIntel.class);
		x.alias("NexFctnBntyIntl", FactionBountyIntel.class);
		x.alias("NexTrbtIntl", TributeIntel.class);
		x.alias("NexTrbtCond", TributeCondition.class);
		x.alias("NexConqMssnMan", ConquestMissionManager.class);	// this will let me change its package later
		x.alias("NexConqMssn", ConquestMissionIntel.class);
		x.alias("NexPirActv", NexPirateActivity.class);
		x.alias("NexPlyrOtpst", PlayerOutpostIntel.class);
		x.alias("NexRlfFlt", ReliefFleetIntelAlt.class);
		x.alias("NexInsurPol", InsuranceIntelV2.InsurancePolicy.class);
		x.alias("NexInsurClm", InsuranceIntelV2.InsuranceClaim.class);
		
		// raids and such
		x.alias("NexRaidIntl", NexRaidIntel.class);
		x.alias("NexOrgStg", NexOrganizeStage.class);
		x.alias("NexAssmblStg", NexAssembleStage.class);
		x.alias("NexTrvlStg", NexTravelStage.class);
		x.alias("NexRetStg", NexReturnStage.class);
		x.alias("NexRaidAssmblStg", NexRaidAssembleStage.class);
		x.alias("NexRaidActStg", NexRaidActionStage.class);
		x.alias("NexBaseStrkIntl", BaseStrikeIntel.class);
		x.alias("NexSatBombIntl", SatBombIntel.class);
		x.alias("NexSatBombActStg", SatBombActionStage.class);
		
		// invasions
		x.alias("NexInvIntl", InvasionIntel.class);
		x.alias("NexInvOrgStg", InvOrganizeStage.class);
		x.alias("NexInvAssmblStg", InvAssembleStage.class);
		x.alias("NexInvActStg", InvActionStage.class);
		x.alias("NexRspwnIntl", RespawnInvasionIntel.class);
		x.alias("NexRspwnBaseIntl", RespawnBaseIntel.class);
		
		// Remnant raids
		x.alias("NexRemRaidIntl", RemnantRaidIntel.class);
		/*
		x.alias("NexRemRaidOrgStg", RemnantRaidOrganizeStage.class);
		x.alias("NexRemRaidAssmblStg", RemnantRaidAssembleStage.class);
		x.alias("NexRemRaidTrvlStg", RemnantRaidTravelStage.class);
		x.alias("NexRemRaidActStg", RemnantRaidActionStage.class);
		x.alias("NexRemRaidRetStg", RemnantRaidReturnStage.class);
		*/
		
		// colony expeditions
		x.alias("ColonyExpdIntl", ColonyExpeditionIntel.class);
		
		// special forces
		x.alias("NexSFIntl", SpecialForcesIntel.class);
		x.alias("NexSFAssgnAI", SpecialForcesAssignmentAI.class);
		
		// agents
		x.alias("NexAgntIntl", AgentIntel.class);
		x.alias("NexAgntActTrvl", Travel.class);
		x.alias("NexAgntActDstb", DestabilizeMarket.class);
		x.alias("NexAgntActDstrCmmdty", DestroyCommodityStocks.class);
		x.alias("NexAgntActInfltrtCell", InfiltrateCell.class);
		x.alias("NexAgntActLowerRel", LowerRelations.class);
		x.alias("NexAgntActRaiseRel", RaiseRelations.class);
		x.alias("NexAgntActSbtgInd", SabotageIndustry.class);
		
		// alliances
		x.alias("NexAlliance", Alliance.class);
		x.alias("NexAllyIntl", AllianceIntel.class);
		x.alias("NexAllyVoteIntl", AllianceVoteIntel.class);
		x.alias("AllyVoteRslt", AllianceVoter.VoteResult.class);
		
		// misc
		x.alias("NexRepAdjustmentResult", ExerelinReputationAdjustmentResult.class);
		x.alias("DiploBrain", DiplomacyBrain.class);
		x.alias("DiploDspsEntry", DiplomacyBrain.DispositionEntry.class);
		x.alias("NexSBAI", Nex_SustainedBurnAbilityAI.class);
		x.alias("NexReinDefCond", ReinforcedDefenses.class);
		x.alias("NexPrcrMssnIntl", Nex_ProcurementMissionIntel.class);
		x.alias("NexBuyColIntl", BuyColonyIntel.class);
		x.alias("NexNavBuoy", Nex_NavBuoyEntityPlugin.class);
		x.alias("NexSensArr", Nex_SensorArrayEntityPlugin.class);
		
		// enums
		x.alias("CovertActionResult", CovertOpsManager.CovertActionResult.class);
		
		configureXStreamAttributes(x);
	}
	
	public static void configureXStreamAttributes(com.thoughtworks.xstream.XStream x)
	{
		// these don't seem to actually work, need to apply on a per-class basis
		//x.aliasAttribute("done", "done");
		//x.aliasAttribute("age", "age");
		//x.aliasAttribute("ended", "ended");
		
		// Alliance
		x.aliasAttribute(Alliance.class, "name", "n");
		x.aliasAttribute(Alliance.class, "uuId", "id");
		x.aliasAttribute(Alliance.class, "alignment", "algn");
		
		// RebellionIntel
		x.aliasAttribute(RebellionIntel.class, "suppressionFleetCountdown", "fltCntdwn");
		x.aliasAttribute(RebellionIntel.class, "govtStrength", "gStr");
		x.aliasAttribute(RebellionIntel.class, "rebelStrength", "rStr");
		x.aliasAttribute(RebellionIntel.class, "govtTradePoints", "gTrd");
		x.aliasAttribute(RebellionIntel.class, "rebelTradePoints", "rTrd");
		x.aliasAttribute(RebellionIntel.class, "suppressionFleet", "flt");
		x.aliasAttribute(RebellionIntel.class, "suppressionFleetWarning", "fltWarn");
		x.aliasAttribute(RebellionIntel.class, "intensity", "intns");
		x.aliasAttribute(RebellionIntel.class, "elapsed", "elpsd");
		x.aliasAttribute(RebellionIntel.class, "stabilityPenalty", "stbLoss");
		x.aliasAttribute(RebellionIntel.class, "result", "rslt");
		x.aliasAttribute(RebellionIntel.class, "conditionToken", "cond");
		
		// VengeanceFleetIntel
		x.aliasAttribute(VengeanceFleetIntel.class, "daysLeft", "days");
		x.aliasAttribute(VengeanceFleetIntel.class, "escalationLevel", "escal");
		x.aliasAttribute(VengeanceFleetIntel.class, "fleet", "flt");
		x.aliasAttribute(VengeanceFleetIntel.class, "foundPlayerYet", "found");
		x.aliasAttribute(VengeanceFleetIntel.class, "timeSpentLooking", "look");
		x.aliasAttribute(VengeanceFleetIntel.class, "trackingMode", "trck");
		
		// SecurityAlertEvent
		x.aliasAttribute(SecurityAlertEvent.class, "elapsedDays", "days");
		x.aliasAttribute(SecurityAlertEvent.class, "alertLevel", "alrt");
		
		// Fleets
		
		// MiningFleetAI
		x.aliasAttribute(MiningFleetAI.class, "daysTotal", "days");
		x.aliasAttribute(MiningFleetAI.class, "miningDailyProgress", "prog");
		x.aliasAttribute(MiningFleetAI.class, "fleet", "flt");
		x.aliasAttribute(MiningFleetAI.class, "orderedReturn", "ret");
		
		// MiningFleetData
		x.aliasAttribute(MiningFleetData.class, "fleet", "flt");
		x.aliasAttribute(MiningFleetData.class, "source", "src");
		x.aliasAttribute(MiningFleetData.class, "target", "tgt");
		x.aliasAttribute(MiningFleetData.class, "sourceMarket", "srcM");
		x.aliasAttribute(MiningFleetData.class, "startingFleetPoints", "strtPt");
		x.aliasAttribute(MiningFleetData.class, "miningStrength", "str");
		
		// VultureFleetAI
		x.aliasAttribute(VultureFleetAI.class, "daysTotal", "days");
		x.aliasAttribute(VultureFleetAI.class, "fleet", "flt");
		x.aliasAttribute(VultureFleetAI.class, "orderedReturn", "ret");
		
		// VultureFleetData
		x.aliasAttribute(VultureFleetData.class, "fleet", "flt");
		x.aliasAttribute(VultureFleetData.class, "source", "src");
		x.aliasAttribute(VultureFleetData.class, "target", "tgt");
		x.aliasAttribute(VultureFleetData.class, "startingFleetPoints", "strtPt");
		
		// ExerelinReputationAdjustmentResult
		x.aliasAttribute(ExerelinReputationAdjustmentResult.class, "wasHostile", "hstl1");
		x.aliasAttribute(ExerelinReputationAdjustmentResult.class, "isHostile", "hstl2");
	}
}
