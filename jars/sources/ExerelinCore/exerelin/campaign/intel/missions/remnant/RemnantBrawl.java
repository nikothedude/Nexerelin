package exerelin.campaign.intel.missions.remnant;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import java.awt.Color;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.BaseFleetEventListener;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Objectives;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.DelayedActionScript;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.intel.missions.BuildStation;
import static exerelin.campaign.intel.missions.remnant.RemnantQuestUtils.getString;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import exerelin.world.ExerelinNewGameSetup;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class RemnantBrawl extends HubMissionWithBarEvent implements FleetEventListener {
	
	public static Logger log = Global.getLogger(RemnantBrawl.class);
	
	public static final float STRAGGLER_LOST_ATTACK_DELAY = 15;
	public static final float STAGING_AREA_FOUND_ATTACK_DELAY = 2.5f;

	public static enum Stage {
		GO_TO_ORIGIN_SYSTEM,
		FOLLOW_STRAGGLER,
		GO_TO_TARGET_SYSTEM,
		BATTLE,
		SCOUT,
		BATTLE_DEFECTED,
		COMPLETED,
		FAILED,
	}
	
	@Deprecated protected PersonAPI dissonant;
	protected MarketAPI stragglerOrigin;
	protected StarSystemAPI stagingArea;
	protected SectorEntityToken stagingPoint;
	protected SectorEntityToken scoutPoint;
	protected CampaignFleetAPI station;
	protected CampaignFleetAPI straggler;
	protected boolean betrayed;
	
	protected PersonAPI admiral;
	
	protected Set<CampaignFleetAPI> createdFleets = new HashSet<>();
	protected Set<CampaignFleetAPI> attackFleets = new HashSet<>();
	
	protected boolean spawnedStraggler;
	protected boolean spawnedAttackFleets;
	protected boolean launchedAttack;
	protected boolean knowStagingArea;
	protected boolean battleInited;
	@Deprecated protected boolean sentFalseInfo;	// not currently sued
	
	// runcode exerelin.campaign.intel.missions.remnant.RemnantBrawl.fixDebug()
	public static void fixDebug() {
		RemnantBrawl mission = (RemnantBrawl)Global.getSector().getMemoryWithoutUpdate().get("$nex_remBrawl_ref");
		for (CampaignFleetAPI fleet : mission.attackFleets) {
			if (fleet == mission.straggler) continue;
			Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), 
					MemFlags.MEMORY_KEY_MAKE_PREVENT_DISENGAGE, "$nex_remBrawl_sus", true, 99999);
		}
	}
	
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
		
		if (!setGlobalReference("$nex_remBrawl_ref")) {
			return false;
		}
		
		/*
		dissonant = getImportantPerson(RemnantQuestUtils.PERSON_DISSONANT);
		
		if (dissonant == null) {
			log.info("Person is null");
			return false;
		}
		personOverride = dissonant;
		*/
		
		station = pickStation();
		if (station == null) return false;
		
		// pick straggler origin
		requireMarketFaction(Factions.HEGEMONY);
		requireMarketNotHidden();
		requireMarketNotInHyperspace();
		preferMarketSizeAtLeast(5);
		preferMarketIsMilitary();
		stragglerOrigin = pickMarket();
		if (stragglerOrigin == null) return false;
		
		// pick staging area
		//requireSystemHasNumPlanets(1);
		requireSystemNotHasPulsar();
		requireSystemTags(ReqMode.NOT_ANY, Tags.THEME_UNSAFE, Tags.THEME_CORE, Tags.THEME_REMNANT);
		requireSystemWithinRangeOf(station.getContainingLocation().getLocation(), 12);
		search.systemReqs.add(new BuildStation.SystemUninhabitedReq());
		preferSystemOutsideRangeOf(station.getContainingLocation().getLocation(), 7);
		// prefer staging areas closer to our start location than the station is
		preferSystemWithinRangeOf(stragglerOrigin.getLocationInHyperspace(), 
				Misc.getDistanceLY(station, stragglerOrigin.getPrimaryEntity()) - 1);
		preferSystemUnexplored();
		stagingArea = pickSystem();
		if (stagingArea == null) return false;
		
		LocData loc = new LocData(EntityLocationType.ORBITING_PLANET_OR_STAR, null, stagingArea);
		stagingPoint = spawnMissionNode(loc);
		if (!setEntityMissionRef(stagingPoint, "$nex_remBrawl_ref")) return false;
		makeImportant(stagingPoint, "$nex_remBrawl_target", Stage.FOLLOW_STRAGGLER);
		stagingPoint.setDiscoverable(true);
		stagingPoint.setSensorProfile(0f);
		stagingPoint.addTag(Tags.NON_CLICKABLE);
		
		setStoryMission();		
				
		makeImportant(station, "$nex_remBrawl_target", Stage.GO_TO_TARGET_SYSTEM, Stage.BATTLE, Stage.BATTLE_DEFECTED);
		//makeImportant(dissonant, "$nex_remM1_returnHere", Stage.RETURN_CORES);
		
		setStartingStage(Stage.GO_TO_ORIGIN_SYSTEM);
		addSuccessStages(Stage.COMPLETED);
		addFailureStages(Stage.FAILED);
		
		beginStageTrigger(Stage.COMPLETED);
		triggerSetGlobalMemoryValue("$nex_remBrawl_missionCompleted", true);
		endTrigger();
		
		beginStageTrigger(Stage.FAILED);
		triggerSetGlobalMemoryValue("$nex_remBrawl_missionFailed", true);
		endTrigger();
		
		// trigger: spawn straggler
		beginWithinHyperspaceRangeTrigger(stragglerOrigin.getPrimaryEntity(), 3, false, Stage.GO_TO_ORIGIN_SYSTEM);
		triggerRunScriptAfterDelay(0, new Script() {
			@Override
			public void run() {
				spawnStragglerFleet();
			}			
		});
		endTrigger();
		
		// trigger: spawn attack fleets and execute attack on delay once player gets close enough to system
		beginWithinHyperspaceRangeTrigger(stagingArea.getHyperspaceAnchor(), 2, false, Stage.FOLLOW_STRAGGLER);
		triggerRunScriptAfterDelay(0, new Script() {
			@Override
			public void run() {
				spawnAttackFleets();
			}
		});
		endTrigger();
		
		// trigger: make Remnant defenders non-hostile
		beginEnteredLocationTrigger(station.getContainingLocation(), Stage.GO_TO_TARGET_SYSTEM, Stage.BATTLE, Stage.BATTLE_DEFECTED);
		triggerRunScriptAfterDelay(0, new Script(){
			@Override
			public void run() {
				setDefendersNonHostile();
			}
		});
		endTrigger();
		
		admiral = OfficerManagerEvent.createOfficer(getHegemony(), 6);
		admiral.setRankId(Ranks.SPACE_ADMIRAL);
		admiral.setPostId(Ranks.POST_FLEET_COMMANDER);
		
		addRelayIfNeeded(station.getStarSystem());
		addRelayIfNeeded(stagingArea);
		
		setRepPersonChangesVeryHigh();
		setRepFactionChangesHigh();
		setCreditReward(CreditReward.VERY_HIGH);
		setCreditReward(this.creditReward * 3);		
		
		return true;
	}
	
	@Override
	public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		Global.getSector().getListenerManager().addListener(this);
	}
	
	protected FactionAPI getHegemony() {
		return Global.getSector().getFaction(Factions.HEGEMONY);
	}
	
	public CampaignFleetAPI pickStation() {
		WeightedRandomPicker<CampaignFleetAPI> picker = new WeightedRandomPicker();
		WeightedRandomPicker<CampaignFleetAPI> pickerFallback = new WeightedRandomPicker();
		WeightedRandomPicker<CampaignFleetAPI> pickerFallback2 = new WeightedRandomPicker();
		Vector2f center = ExerelinNewGameSetup.SECTOR_CENTER;
		for (StarSystemAPI system : Global.getSector().getStarSystems()) 
		{
			if (!system.hasTag(Tags.THEME_REMNANT)) continue;
			boolean highPower = system.hasTag(Tags.THEME_REMNANT_RESURGENT);
			
			for (CampaignFleetAPI fleet : system.getFleets()) 
			{
				if (!Factions.REMNANTS.equals(fleet.getFaction().getId())) 
					continue;
				
				if (fleet.isStationMode()) 
				{
					float dist = MathUtils.getDistance(fleet.getLocation(), center);
					float weight = 50000/dist;
					if (weight > 20) weight = 20;
					if (weight < 0.1f) weight = 0.1f;
					if (highPower && dist <= 20000) picker.add(fleet, weight);
					else if (highPower) pickerFallback.add(fleet, weight);
					else pickerFallback2.add(fleet, weight);
				}
			}
		}
		CampaignFleetAPI base = picker.pick();
		if (base == null) base = pickerFallback.pick();
		if (base == null) base = pickerFallback2.pick();
		return base;
	}
	
	/**
	 * Adds a relay to the system (if possible, and one does not already exist).<br/>
	 * This is so the scout mission makes more sense.
	 * @param system
	 */
	protected void addRelayIfNeeded(StarSystemAPI system) {
		boolean haveRelay = !system.getEntitiesWithTag(Tags.COMM_RELAY).isEmpty();
		if (haveRelay) return;
		
		for (SectorEntityToken sLoc : system.getEntitiesWithTag(Tags.STABLE_LOCATION)) {
			Objectives o = new Objectives(sLoc);
			o.build(Entities.COMM_RELAY_MAKESHIFT, Factions.REMNANTS);
			break;
		}
	}
	
	protected void spawnStragglerFleet() {
		if (straggler != null) return;
		
		float fp = 120;
		FleetParamsV3 params = new FleetParamsV3(stragglerOrigin, FleetTypes.TASK_FORCE, 
				fp, // combat
				fp * 0.1f,	// freighters
				fp * 0.1f,		// tankers
				0,		// personnel transports
				0,		// liners
				0,	// utility
				0.15f);	// quality mod
		params.officerNumberMult = 1.2f;
		params.averageSMods = 2;
		params.random = this.genRandom;
		
		CampaignFleetAPI fleet = spawnFleet(params, stragglerOrigin.getPrimaryEntity());
		attackFleets.add(fleet);
		fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, stragglerOrigin.getPrimaryEntity(), 3);
		fleet.addAssignment(FleetAssignment.DELIVER_RESOURCES, stagingPoint, 1000, 
				StringHelper.getFleetAssignmentString("travellingTo", StringHelper.getString("unknownLocation")), 
				new Script() {
					@Override
					public void run() {
						checkAttack();
					}
				});
		fleet.getMemoryWithoutUpdate().set("$startingFP", fleet.getFleetPoints());
		makeImportant(fleet, "$nex_remBrawl_attackFleet", Stage.FOLLOW_STRAGGLER, Stage.GO_TO_TARGET_SYSTEM, Stage.BATTLE);
		fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
		
		fleet.addEventListener(new BaseFleetEventListener() {
			public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
				spawnAttackFleets();
				Global.getSector().addScript(new DelayedActionScript(STRAGGLER_LOST_ATTACK_DELAY) {
					@Override
					public void doAction() {
						checkAttack();
					}
				});
			}
		});
		
		fleet.getCommanderStats().setSkillLevel(Skills.NAVIGATION, 1);
		addTugsToFleet(fleet, 1, genRandom);
		
		setCurrentStage(Stage.FOLLOW_STRAGGLER, null, null);
		
		straggler = fleet;
	}
		
	protected void spawnAttackFleets() {
		if (spawnedAttackFleets) return;
		
		/*
			Proposed composition:
			3 big Heg fleets, maybe the latter two are slightly smaller
			1 big LC fleet
			2 semi-big fleets, chance of being LC or Hegemony allies
			switch fleet to Hegemony if the faction isn't allied
		*/
		int fp = 150;
		for (int i=0; i<3; i++) {
			spawnAttackFleet(Factions.HEGEMONY, fp);
		}
		spawnAttackFleet(Factions.LUDDIC_CHURCH, fp);
		
		fp = 120;
		WeightedRandomPicker<String> factionPicker = new WeightedRandomPicker<>(this.genRandom);
		factionPicker.add(Factions.LUDDIC_CHURCH, 2);
		factionPicker.add(Factions.HEGEMONY);
		if (AllianceManager.getFactionAlliance(Factions.HEGEMONY) != null) {
			factionPicker.addAll(AllianceManager.getFactionAlliance(Factions.HEGEMONY).getMembersCopy());
		}
		for (int i=0; i<2; i++) {
			spawnAttackFleet(factionPicker.pick(), fp);
		}
				
		spawnExtraDefenders();
		
		spawnedAttackFleets = true;
	}
	
	protected CampaignFleetAPI spawnAttackFleet(String factionId, int fp) {
		FactionAPI heg = getHegemony();
		
		FleetParamsV3 params = new FleetParamsV3(stagingPoint.getLocationInHyperspace(),
				factionId,
				null,	// quality override
				FleetTypes.TASK_FORCE,
				fp, // combat
				fp * 0.1f,	// freighters
				fp * 0.1f,		// tankers
				0,		// personnel transports
				0,		// liners
				0,	// utility
				0.15f);	// quality mod
		params.officerNumberMult = 1.2f;
		params.averageSMods = 2;
		params.maxNumShips = (int)(Global.getSettings().getInt("maxShipsInAIFleet") * 1.2f);
		params.random = this.genRandom;
		
		CampaignFleetAPI fleet = spawnFleet(params, stagingPoint);
		fleet.getCommanderStats().setSkillLevel(Skills.NAVIGATION, 1);
		addTugsToFleet(fleet, 1, genRandom);
		
		attackFleets.add(fleet);
		// needs to be ORBIT_AGGRESSIVE to pursue player
		fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, stagingPoint, 99999, StringHelper.getFleetAssignmentString("rendezvous", null));
		fleet.getMemoryWithoutUpdate().set("$genericHail", true);
		
		makeImportant(fleet, "$nex_remBrawl_attackFleet", Stage.FOLLOW_STRAGGLER, Stage.GO_TO_TARGET_SYSTEM, Stage.BATTLE);
		
		// don't keep original faction if it risks them becoming hostile to Hegemony shortly
		if (heg.isAtBest(factionId, RepLevel.FAVORABLE) || factionId.equals(Misc.getCommissionFactionId())) 
		{
			fleet.setFaction(Factions.HEGEMONY, true);
		}
		
		// fleets are sus and interrogate player
		Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), 
				MemFlags.MEMORY_KEY_PURSUE_PLAYER, "$nex_remBrawl_sus", true, 99999);
		Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), 
					MemFlags.MEMORY_KEY_MAKE_PREVENT_DISENGAGE, "$nex_remBrawl_sus", true, 99999);
		fleet.getMemoryWithoutUpdate().set("$genericHail_openComms", "Nex_RemBrawlSusHail");
		
		return fleet;
	}
	
	protected void spawnExtraDefenders() {
		float fp = 45;
		
		for (int i=0; i<=2; i++) {
			FleetParamsV3 params = new FleetParamsV3(station.getLocationInHyperspace(),
					Factions.MERCENARY,
					1f,
					FleetTypes.MERC_ARMADA,
					fp, // combat
					fp * 0.1f,	// freighters
					fp * 0.1f,		// tankers
					0,		// personnel transports
					0,		// liners
					3,	// utility
					0);	// quality mod
			params.averageSMods = 4;
			params.random = this.genRandom;
			CampaignFleetAPI fleet = spawnFleet(params, station);
			fleet.setFaction(Factions.REMNANTS, false);
			fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, station, 3000);
			fleet.getMemoryWithoutUpdate().set("$nex_remBrawl_merc", true);
		}
	}
	
	protected CampaignFleetAPI spawnFleet(FleetParamsV3 params, SectorEntityToken loc) {
		CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
		createdFleets.add(fleet);
		fleet.getMemoryWithoutUpdate().set("$startingFP", fleet.getFleetPoints());
		fleet.getMemoryWithoutUpdate().set("$nex_remBrawl_ref", this);
		
		loc.getContainingLocation().addEntity(fleet);
		fleet.setLocation(loc.getLocation().x, loc.getLocation().y);
		
		return fleet;
	}
	
	/**
	 * Check if we should order the attack. Called when the straggler arrives, 
	 * or after some days of it being killed without arriving.
	 */
	public void checkAttack() {
		// if player is scouting the system first, or we already ordered the attack, do nothing
		if (launchedAttack) return;
		if (currentStage == Stage.SCOUT) return;
		
		orderAttack();
	}
	
	public void orderAttack() {
		if (launchedAttack) return;
		
		SectorEntityToken targetToken = station.getContainingLocation().createToken(station.getLocation().x, station.getLocation().y);
		
		for (CampaignFleetAPI fleet : attackFleets) {
			fleet.clearAssignments();
			fleet.addAssignment(betrayed ? FleetAssignment.DELIVER_MARINES : FleetAssignment.ATTACK_LOCATION, 
					targetToken, 60, StringHelper.getFleetAssignmentString("attacking", station.getName()));
			fleet.addAssignment(FleetAssignment.INTERCEPT, station, 20);			
		}
		straggler.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
		straggler.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS);
		
		unsetFleetSus();
		
		launchedAttack = true;
	}
	
	/**
	 * Check if we've found the staging area, by seeing the point (which is invisible though) 
	 * or any fleets other than the straggler.
	 */
	public void checkStagingAreaFound() {
		if (knowStagingArea) return;
		
		boolean found = false;
		if (!stagingPoint.isDiscoverable()) {
			log.info("Found staging point");
			found = true;
		} else {
			for (CampaignFleetAPI fleet : attackFleets) {
				if (fleet == straggler) continue;
				if (fleet.getContainingLocation() != station.getContainingLocation() && !Misc.isNear(fleet, stagingArea.getLocation())) continue;
				if (fleet.getVisibilityLevelOfPlayerFleet() == VisibilityLevel.COMPOSITION_DETAILS) {
					found = true;
					log.info("Spotted fleet " + fleet.getName());
					break;
				}
			}
		}
		if (found) {
			knowStagingArea = true;
			stagingPoint.setDiscoverable(false);
			if (currentStage == Stage.FOLLOW_STRAGGLER) {
				setCurrentStage(Stage.GO_TO_TARGET_SYSTEM, null, null);
			}
			Global.getSector().addScript(new DelayedActionScript(STAGING_AREA_FOUND_ATTACK_DELAY) {
				@Override
				public void doAction() {
					checkAttack();
				}
			});
			Global.getSector().getCampaignUI().addMessage("Found staging area");
		}
	}
	
	/**
	 * Check if we should go to the battle stage, due to the attackers entering the target system.
	 */
	public void checkAdvanceToBattleStage() {
		if (battleInited) return;
		if (currentStage == Stage.BATTLE || currentStage == Stage.BATTLE_DEFECTED) return;
		for (CampaignFleetAPI fleet : attackFleets) {
			if (fleet.getContainingLocation() == station.getContainingLocation()) {
				initBattleStage(null, null);
				break;
			}
		}
	}
	
	/**
	 * Begin the battle stage.
	 * @param dialog
	 * @param memoryMap
	 */
	public void initBattleStage(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		if (battleInited) return;
		if (!betrayed) {
			makeAttackersHostile();
		}
		setCurrentStage(betrayed ? Stage.BATTLE_DEFECTED : Stage.BATTLE, dialog, memoryMap);
		
		battleInited = true;
	}
	
	/**
	 * After agreeing to scout the target system for the Hegemony admiral.
	 * @param dialog
	 * @param memoryMap
	 */
	protected void gotoScoutStage(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		scoutPoint = spawnMissionNode(new LocData(EntityLocationType.ORBITING_PARAM, 
				station, station.getStarSystem()));
		setEntityMissionRef(scoutPoint, "$nex_remBrawl_ref");
		makeImportant(scoutPoint, "$nex_remBrawl_target", Stage.SCOUT);
		
		setCurrentStage(Stage.SCOUT, dialog, memoryMap);
	}
	
	public void makeAttackersHostile() {
		for (CampaignFleetAPI fleet : attackFleets) {
			Misc.makeLowRepImpact(fleet, "$nex_remBrawl");
			Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_HOSTILE, "$nex_remBrawl", true, 90);
		}
	}
	
	public void unsetFleetSus() {
		for (CampaignFleetAPI fleet : attackFleets) {
			Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_PURSUE_PLAYER, "$nex_remBrawl_sus", false, 0);
			Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, "$pursue", false, 0);
			//fleet.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE);
			Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_PREVENT_DISENGAGE, "$nex_remBrawl_sus", false, 0);
			fleet.getMemoryWithoutUpdate().unset("$genericHail");
			fleet.getMemoryWithoutUpdate().unset("$genericHail_openComms");
		}
	}
	
	public void setDefendersNonHostile() {
		for (CampaignFleetAPI fleet : station.getContainingLocation().getFleets()) {
			if (fleet.getFaction().getId().equals(Factions.REMNANTS)) {
				Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, "$nex_remBrawl_def", true, 90);
			}
		}
	}
	
	public void unsetDefendersNonHostile() {
		for (CampaignFleetAPI fleet : station.getContainingLocation().getFleets()) {
			if (fleet.getFaction().getId().equals(Factions.REMNANTS)) {
				Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, "$nex_remBrawl_def", false, 0);
			}
		}
	}
	
	public void checkAttackFleetDefeated(CampaignFleetAPI fleet) {
		if (!attackFleets.contains(fleet)) return;
		
		int fp = fleet.getFleetPoints();
		if (fp < fleet.getMemoryWithoutUpdate().getFloat("$startingFP")) {
			log.info("Removing attacker fleet " + fleet.getFullName() + " due to excessive damage");
			attackFleets.remove(fleet);
			checkRemnantVictory();
		}
	}
	
	public void checkRemnantVictory() {
		boolean won = (currentStage == Stage.BATTLE || currentStage == Stage.GO_TO_TARGET_SYSTEM) 
				&& attackFleets.isEmpty();
		
		if (!won) return;
		if (betrayed) {
			// do nothing, let player finish the job themselves
		} else {
			setCurrentStage(Stage.COMPLETED, null, null);
			getPerson().setImportance(getPerson().getImportance().next());
			ContactIntel ci = ContactIntel.getContactIntel(getPerson());
			if (ci != null) ci.sendUpdateIfPlayerHasIntel(null, false, false);
		}
	}
	
	public void hegemonyVictory() {
		if (betrayed) {
			PersonAPI midnight = getPerson();
			setPersonOverride(admiral);
			
			setCurrentStage(Stage.COMPLETED, null, null);
			NexUtilsReputation.adjustPlayerReputation(Global.getSector().getFaction(Factions.REMNANTS), 
					midnight, -0.15f, -0.3f, null, null);
			unsetDefendersNonHostile();
			
			// on betray route, remove Midnight and her intel
			if (midnight.getMarket() != null) 
				midnight.getMarket().getCommDirectory().removePerson(midnight);
			ContactIntel ci = ContactIntel.getContactIntel(midnight);
			if (ci != null) {
				ci.endAfterDelay();
				ci.sendUpdateIfPlayerHasIntel(null, false, false);
			}
			
		} else {
			setCurrentStage(Stage.FAILED, null, null);
		}
	}
	
	protected void cleanup() {
		Global.getSector().getListenerManager().removeListener(this);
		for (CampaignFleetAPI fleet : createdFleets) {
			if (!fleet.isAlive()) continue;
			Misc.giveStandardReturnToSourceAssignments(fleet, true);
			Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_HOSTILE, "$nex_remBrawl", false, 9999);
		}
		stagingPoint.getContainingLocation().removeEntity(stagingPoint);
	}
	
	@Override
	protected void notifyEnding() {
		super.notifyEnding();
		cleanup();
	}
	
	@Override
	protected void advanceImpl(float amount) {
		super.advanceImpl(amount);
		checkStagingAreaFound();
		checkAdvanceToBattleStage();
	}
	
	@Override
	protected void updateInteractionDataImpl() {
		set("$nex_remBrawl_reward", Misc.getWithDGS(getCreditsReward()));
		
		set("$nex_remBrawl_stragglerOriginName", stragglerOrigin.getName());
		set("$nex_remBrawl_targetSystemName", station.getStarSystem().getBaseName());
		set("$nex_remBrawl_stagingAreaName", stagingArea.getBaseName());
		
		set("$nex_remBrawl_admiral_heOrShe", admiral.getHeOrShe());
		set("$nex_remBrawl_admiral_HeOrShe", Misc.ucFirst(admiral.getHeOrShe()));
		set("$nex_remBrawl_admiral_himOrHer", admiral.getHimOrHer());
		set("$nex_remBrawl_admiral_HimOrHer", Misc.ucFirst(admiral.getHimOrHer()));
		set("$nex_remBrawl_admiral_hisOrHer", admiral.getHisOrHer());
		set("$nex_remBrawl_admiral_HisOrHer", Misc.ucFirst(admiral.getHisOrHer()));
		set("$nex_remBrawl_admiral", admiral);
		
		set("$nex_remBrawl_stage", getCurrentStage());
	}
	
	@Override
	public boolean callEvent(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		String action = params.get(0).getString(memoryMap);
		
		switch (action) {
			case "showAdmiral":
				dialog.getVisualPanel().showSecondPerson(admiral);
				return true;
			case "violentEncounter":
				orderAttack();
				makeAttackersHostile();
				unsetFleetSus();
				return true;
			case "agreeScout":
				gotoScoutStage(dialog, memoryMap);
				unsetFleetSus();
				return true;
			case "scoutTrue":
				betrayed = true;
				orderAttack();
				initBattleStage(dialog, memoryMap);
				scoutPoint.getContainingLocation().removeEntity(scoutPoint);
				return true;
			case "scoutFalse":
				sentFalseInfo = true;
				orderAttack();
				initBattleStage(dialog, memoryMap);
				scoutPoint.getContainingLocation().removeEntity(scoutPoint);
				return true;
			default:
				break;
		}
		
		return super.callEvent(ruleId, dialog, params, memoryMap);
	}
	
	@Override
	protected void addBulletPointsPre(TooltipMakerAPI info, Color tc, float initPad, ListInfoMode mode) {
		if (mode == ListInfoMode.MESSAGES && currentStage == Stage.GO_TO_TARGET_SYSTEM) {
			info.addPara(getString("brawl_foundStagingAreaUpdate"), initPad, tc, 
					Misc.getHighlightColor(), station.getContainingLocation().getNameWithTypeIfNebula());
		}
	}
	
	@Override
	public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		Color h = Misc.getHighlightColor();
		
		String str = getString("brawl_boilerplateDesc");
		str = StringHelper.substituteToken(str, "$name", getPerson().getName().getFullName());
		info.addPara(str, opad);
		
		if (ExerelinModPlugin.isNexDev) {
			info.addPara("[debug] We are now in stage: " + currentStage, opad);
			//info.addPara("[debug] Staging area found: " + knowStagingArea, opad);
			info.addPara("[debug] Station is in: " + station.getContainingLocation().getNameWithLowercaseTypeShort(), 0);
			info.addPara("[debug] Staging area: " + stagingArea.getNameWithLowercaseTypeShort(), 0);
		}
		Color col = station.getStarSystem().getStar().getSpec().getIconColor();
		String sysName = station.getContainingLocation().getNameWithLowercaseTypeShort();
		
		if (currentStage == Stage.GO_TO_ORIGIN_SYSTEM || currentStage == Stage.FOLLOW_STRAGGLER) 
		{
			info.addPara(getString("brawl_startDesc"), opad, 
					stragglerOrigin.getFaction().getBaseUIColor(), stragglerOrigin.getName());
		}
		else if (currentStage == Stage.GO_TO_TARGET_SYSTEM) {
			info.addPara(getString("brawl_foundStagingAreaDesc"), opad, col, sysName);
		}
		else if (currentStage == Stage.SCOUT) {
			info.addPara(getString("brawl_scoutDesc"), opad, col, sysName);
		}
		else if (currentStage == Stage.BATTLE) {
			info.addPara(getString("brawl_battleDesc" + (knowStagingArea ? "" : "Unknown")), 
					opad, col, sysName);
		}
		else if (currentStage == Stage.BATTLE_DEFECTED) {
			info.addPara(getString("brawl_battleBetrayDesc"), opad, col, sysName);
		}
	}

	@Override
	public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
		Color col = station.getStarSystem().getStar().getSpec().getIconColor();
		String sysName = station.getContainingLocation().getNameWithLowercaseTypeShort();
		
		//info.addPara("[debug] Current stage: " + currentStage, tc, pad);
		
		if (currentStage == Stage.GO_TO_ORIGIN_SYSTEM || currentStage == Stage.FOLLOW_STRAGGLER) {
			info.addPara(getString("brawl_startNextStep"), 0, tc, 
					stragglerOrigin.getFaction().getBaseUIColor(), stragglerOrigin.getName());
		} 
		else if (currentStage == Stage.GO_TO_TARGET_SYSTEM) {
			info.addPara(getString("brawl_foundStagingAreaNextStep"), 0, tc, col, sysName);
			info.addPara(getString("brawl_foundStagingAreaNextStep2"), tc, 0);
		} 
		else if (currentStage == Stage.SCOUT) {
			info.addPara(getString("brawl_scoutNextStep" + (knowStagingArea ? "" : "Unknown")), 0, col, sysName);
		}
		else if (currentStage == Stage.BATTLE) {
			info.addPara(getString("brawl_battleNextStep" + (knowStagingArea ? "" : "Unknown")), 0, col, sysName);
		}
		else if (currentStage == Stage.BATTLE_DEFECTED) {
			info.addPara(getString("brawl_battleBetrayNextStep"), 0, col, sysName);
		}
		return false;
	}
	
	@Override
	protected boolean shouldSendUpdateForStage(Object id) {
		return id != Stage.FOLLOW_STRAGGLER;
	}

	@Override
	public String getBaseName() {
		return getString("brawl_name");
	}

	@Override
	public String getPostfixForState() {
		if (startingStage != null) {
			return "";
		}
		return super.getPostfixForState();
	}	

	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map, Object currentStage) {
		if (currentStage == Stage.FOLLOW_STRAGGLER) {
			if (straggler != null && straggler.isVisibleToPlayerFleet()) return straggler;
		}
		if (currentStage == Stage.FOLLOW_STRAGGLER || currentStage == Stage.GO_TO_ORIGIN_SYSTEM) {
			return stragglerOrigin.getPrimaryEntity();
		}
		if (knowStagingArea) {
			return station;
		}
		
		return null;
	}
	
	@Override
	public List<ArrowData> getArrowData(SectorMapAPI map) {
		if (currentStage == Stage.GO_TO_TARGET_SYSTEM && knowStagingArea) {
			List<ArrowData> result = new ArrayList<>();
			ArrowData arrow = new ArrowData(stagingArea.getHyperspaceAnchor(), station);
			arrow.color = getHegemony().getBaseUIColor();
			arrow.width = 10f;
			result.add(arrow);
			
			return result;
		}
		return null;
	}
	
	
	@Override
	public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
		if (attackFleets.contains(fleet)) {
			attackFleets.remove(fleet);
			checkRemnantVictory();
		}
		if (fleet == station) {
			hegemonyVictory();
		}
	}

	@Override
	public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
		for (CampaignFleetAPI participant : battle.getBothSides()) {
			checkAttackFleetDefeated(participant);
		}
	}
}




