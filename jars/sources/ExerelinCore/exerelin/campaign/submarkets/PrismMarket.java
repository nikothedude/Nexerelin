package exerelin.campaign.submarkets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemQuantity;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CoreUIAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickParams;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction.ShipSaleInfo;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.ShipRolePick;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.ShipRoles;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.campaign.intel.SWP_IBBIntel.FamousBountyStage;
import data.scripts.campaign.intel.SWP_IBBTracker;
import exerelin.ExerelinConstants;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.StringHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PrismMarket extends BaseSubmarketPlugin {

    //public static final int NUMBER_OF_SHIPS = 16;
    //public static final int MAX_WEAPONS = 27;
    public static final RepLevel MIN_STANDING = RepLevel.NEUTRAL;
    public static final String IBB_FILE = "data/config/prism/prism_boss_ships.csv";
    public static final String SHIPS_BLACKLIST = "data/config/prism/prism_ships_blacklist.csv";
    public static final String WEAPONS_BLACKLIST = "data/config/prism/prism_weapons_blacklist.csv";
	public static final String BLUEPRINTS_BLACKLIST = "data/config/prism/prism_blueprints_blacklist.csv";
    public static final String FACTION_WHITELIST = "data/config/prism/prism_factions_whitelist.csv";
    public static final String ILLEGAL_TRANSFER_MESSAGE = StringHelper.getString("exerelin_markets", "prismNoSale");
    public static final Set<String> DISALLOWED_FACTIONS = new HashSet<>(Arrays.asList(new String[] {
        "templars", Factions.DERELICT, Factions.REMNANTS, Factions.PIRATES
    }));
    public static final Set<String> DISALLOWED_PREFIXES = new HashSet<>(Arrays.asList(new String[] {
        "tem_"
    }));
    
    public static Logger log = Global.getLogger(PrismMarket.class);
    //protected float minCargoUpdateInterval = 0;    // debugging
    
    protected static Set<String> restrictedWeapons;
    protected static Set<String> restrictedShips;
	protected static Set<String> restrictedBlueprints;
    protected static Set<String> allowedFactions;
    
    protected static Set<SubmarketAPI> cachedSubmarkets = null;
    
    protected Set<String> alreadyBoughtShips = new HashSet<>();
    
    static {
        try {
            setupBlacklists();
        } catch (JSONException | IOException ex) {
            log.error(ex);
        }
    }
    
    public static String getIBBFile() {
        return IBB_FILE;
    }
    
    @Override
    public void init(SubmarketAPI submarket) {
        super.init(submarket);
    }
    
    public static Set getRestrictedShips()
    {
        return new HashSet<>(restrictedShips);
    }
    
    public static Set getRestrictedWeapons()
    {
        return new HashSet<>(restrictedWeapons);
    }

    @Override
    public void updateCargoPrePlayerInteraction() 
	{
        log.info("Days since update: "+ sinceLastCargoUpdate);
        if (sinceLastCargoUpdate<30) return;
        sinceLastCargoUpdate = 0f;
        
        CargoAPI cargo = getCargo();

        //pruneWeapons(1f);
        for (CargoStackAPI s : cargo.getStacksCopy()) {
            if(Math.random()>0.5f){
                float qty = s.getSize();
                cargo.removeItems(s.getType(), s.getData(), qty );
                cargo.removeEmptyStacks();
            }
        }
        addShips();
        addWings();
        addWeapons();
        cargo.sort();
    }
    
    public boolean isShipAllowed(FleetMemberAPI member, float requiredFP)
    {
        if (member.getHullSpec().isDHull()) return false;
        if (member.getFleetPointCost() < requiredFP) return false; //quality check
        if (restrictedShips.contains(member.getHullSpec().getBaseHullId())) return false;
        if (member.getHullSpec().getHints().contains(ShipTypeHints.STATION)) return false;
        
        return true;
    }
    
    public boolean isWingAllowed(FighterWingSpecAPI spec)
    {
        if (spec.getTier() < 2) return false;
        if (spec.getTier() >= 5) return false;
        if (spec.hasTag(Tags.WING_NO_SELL)) return false;
        String specId = spec.getId();
        for (String prefix : DISALLOWED_PREFIXES)
        {
            if (specId.startsWith(prefix))
                return false;
        }
        if (restrictedShips.contains(specId)) return false;
        return true;
    }
    
    public boolean isWeaponAllowed(WeaponSpecAPI spec)
    {
        if (spec.getTier() < 2) return false;
        String specId = spec.getWeaponId();
        for (String prefix : DISALLOWED_PREFIXES)
        {
            if (specId.startsWith(prefix))
                return false;
        }
        if (restrictedWeapons.contains(specId)) return false;
        return true;
    }
    
    // same as (old) vanilla one except without inflated weights for some weapons like HMG and Light Needler
    protected void addRandomWeapons(int max, int maxTier) {
        CargoAPI cargo = getCargo();
        List<String> weaponIds = Global.getSector().getAllWeaponIds();
        
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<String>();
        if (!Global.getSettings().isDevMode() || true) {
            picker.setRandom(itemGenRandom);
        }
        
        //float qualityFactor = market.getShipQualityFactor(); 
        for (String id : weaponIds) {
            WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(id);
            if (spec.getTier() <= maxTier && isWeaponAllowed(spec)) {
                //float weaponQuality = 0.33f * (float) spec.getTier();
                //float qualityDiff = Math.abs(qualityFactor - weaponQuality);
                float qualityDiff = 0f;
                float f = Math.max(0, 1f - qualityDiff);
                float weight = Math.max(0.05f, f * f);
                
                weight *= spec.getRarity();
                
                picker.add(spec.getWeaponId(), weight);
            }
        }
        if (!picker.isEmpty()) {
            for (int i = 0; i < max; i++) {
                String weaponId = picker.pick();
                int quantity = 2;
                cargo.addWeapons(weaponId, quantity);
            }    
        }
    }
    
    protected void addWeapons()
    {
        CargoAPI cargo = getCargo();
        
        float variation=(float)Math.random()*0.5f+0.75f;
        for ( float i=0f; i < ExerelinConfig.prismMaxWeapons*variation; i = cargo.getWeapons().size()) {
            addRandomWeapons(10, 4);
        }
    }
    
    protected void addWings()
    {
        CargoAPI cargo = getCargo();
        WeightedRandomPicker<String> fighterPicker = new WeightedRandomPicker<>();
        for (FighterWingSpecAPI spec : Global.getSettings().getAllFighterWingSpecs()) {
            if (isWingAllowed(spec))
                fighterPicker.add(spec.getId());
        }
        
        int picks = 0;
        for (CargoItemQuantity<String> quantity : cargo.getFighters())
        {
            picks += quantity.getCount();
        }
        while (!fighterPicker.isEmpty() && picks < ExerelinConfig.prismNumWings) {
            String id = fighterPicker.pick();        
            cargo.addItems(CargoAPI.CargoItemType.FIGHTER_CHIP, id, 1);
            picks++;
        }
    }

    protected void addShips() {
        
        CargoAPI cargo = getCargo();
        FleetDataAPI data = cargo.getMothballedShips();
        Set<String> allBossShips = getAllBossShips();
        
        //remove half the stock (and all boss ships)
        for (FleetMemberAPI member : data.getMembersListCopy()) {
            if (allBossShips.contains(member.getHullId())) data.removeFleetMember(member);
            else if (Math.random()>0.5f) data.removeFleetMember(member);                
        }

        WeightedRandomPicker<String> rolePicker = new WeightedRandomPicker<>();
        rolePicker.add(ShipRoles.CIV_RANDOM, 1f);
        rolePicker.add(ShipRoles.FREIGHTER_SMALL, 1f);
        rolePicker.add(ShipRoles.FREIGHTER_MEDIUM, 1f);
        rolePicker.add(ShipRoles.FREIGHTER_LARGE, 5f);
        rolePicker.add(ShipRoles.TANKER_SMALL, 1f);
        rolePicker.add(ShipRoles.TANKER_MEDIUM, 1f);
        rolePicker.add(ShipRoles.TANKER_LARGE, 1f);
        rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1f);
        rolePicker.add(ShipRoles.COMBAT_FREIGHTER_MEDIUM, 1f);
        rolePicker.add(ShipRoles.COMBAT_FREIGHTER_LARGE, 5f);
        rolePicker.add(ShipRoles.COMBAT_SMALL, 25f);
        rolePicker.add(ShipRoles.COMBAT_MEDIUM, 30f);
        rolePicker.add(ShipRoles.COMBAT_LARGE, 25f);
        rolePicker.add(ShipRoles.COMBAT_CAPITAL, 15f);
        rolePicker.add(ShipRoles.CARRIER_SMALL, 5f);
        rolePicker.add(ShipRoles.CARRIER_MEDIUM, 5f);
        rolePicker.add(ShipRoles.CARRIER_LARGE, 5f);

        WeightedRandomPicker<FactionAPI> factionPicker = new WeightedRandomPicker<>();
        SectorAPI sector = Global.getSector();
        for (String factionId: allowedFactions) {
            FactionAPI faction = sector.getFaction(factionId);
            if (faction == null) continue;
            if (!faction.isShowInIntelTab()) continue;
            //if (faction.isNeutralFaction()) continue;
            //if (faction.isPlayerFaction()) continue;
            if (DISALLOWED_FACTIONS.contains(factionId)) continue;
            factionPicker.add(sector.getFaction(factionId));
        }
        
        //renew the stock
        float variation=(float)Math.random()*0.5f+0.75f;
        int tries = 0;
        for (int i=0; i<ExerelinConfig.prismNumShips*variation; i=cargo.getMothballedShips().getNumMembers()){
            //pick the role and faction
            List<ShipRolePick> picks = null;
            int tries2 = 0;
            do {
                tries2++;
                FactionAPI faction = factionPicker.pick();
                String role = rolePicker.pick();
                //pick the random ship
                try {
                    picks = faction.pickShip(role, ShipPickParams.priority());
                } catch (NullPointerException npex) {
                    // likely picker picked a role when faction has no ships for that role; do nothing
                }
            } while (picks == null);
            
            for (ShipRolePick pick : picks) {
                FleetMemberType type = FleetMemberType.SHIP;
                String variantId = pick.variantId; 
                                
                //set the ID
                FleetMemberAPI member = Global.getFactory().createFleetMember(type, variantId);
                variantId = member.getHullId() + "_Hull";
                member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
               
                // Fleet point cost threshold
                int FP;
                if (member.isCapital()){
                    FP = 20;
                } else if (member.isCruiser()){
                    FP = 14;
                } else if (member.isDestroyer()){
                    FP = 10;
                } else if (member.isFrigate()){
                    FP = 5;
                } else {
                    FP = 6;
                }
                
                //if the variant is not degraded and high end, add it. Else start over
                if (isShipAllowed(member, FP))
                {
                    member.getRepairTracker().setMothballed(true);
                    getCargo().getMothballedShips().addFleetMember(member);
                } else { 
                    i-=1;
                }
            }
			tries++;
			//log.info("Add ship try " + tries);
			if (tries > 40) break;
        }
        
        //add some IBBs
        List<String> bossShips = getBossShips();
        for (String variantId : bossShips)
        {
            try { 
                FleetMemberAPI member;
                if (variantId.endsWith("_wing")) {
                    getCargo().addFighters(variantId, 1);
                }
                else { 
                    variantId += "_Hull";
                    member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
                    member.getRepairTracker().setMothballed(true);
                    getCargo().getMothballedShips().addFleetMember(member);
                }
            } catch (RuntimeException rex) {
                // ship doesn't exist; meh
            }
        }
    }
    
    /**
     * Gets a set of all boss ships in the merged definition .csv
     * @return
     */
    public Set<String> getAllBossShips() {
        Set<String> bossShips = new HashSet<>();
        try {
            JSONArray config = Global.getSettings().getMergedSpreadsheetDataForMod("id", getIBBFile(), ExerelinConstants.MOD_ID);
            for(int i = 0; i < config.length(); i++) {
            
                JSONObject row = config.getJSONObject(i);
                String hullId = row.getString("id");
                String factionId = row.optString("faction");
                
                if (!canLoadShips(factionId)) continue;
                bossShips.add(hullId);    
            }
        } catch (IOException | JSONException ex) {
            log.error(ex);
        }
        return bossShips;
    }
    
    /**
     * Gets a limited number of boss ships to add to the market stocks
     * @return
     */
    public List<String> getBossShips() {
        
        List<BossShipEntry> validShips = new ArrayList<>();
        List<String> ret = new ArrayList<>();
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        
        int ibbProgress = 999;
        boolean doIBBCheck = ExerelinConfig.prismUseIBBProgressForBossShips && ExerelinModPlugin.HAVE_SWP;
        int highestIBBNum = 0;
        
        try {
            JSONArray config = Global.getSettings().getMergedSpreadsheetDataForMod("id", getIBBFile(), ExerelinConstants.MOD_ID);
            for(int i = 0; i < config.length(); i++) {
            
                JSONObject row = config.getJSONObject(i);
                String hullId = row.getString("id");
                String factionId = row.optString("faction");
                
                if (!canLoadShips(factionId)) continue;
                
                int ibbNum = row.getInt("ibbNum");
                String stage = row.optString("stage");
                validShips.add(new BossShipEntry(hullId, ibbNum, stage));    
                
                if (ibbNum > highestIBBNum) 
                    highestIBBNum = ibbNum;
            }
                
            // ensure proper emphasis on last ships once IBB sidequest is complete
            if (ibbProgress == 999)
                ibbProgress = highestIBBNum;
            
            for (BossShipEntry entry : validShips) {
                boolean proceed = true;
                String stageStr = entry.stage;
                if (doIBBCheck && stageStr != null && !stageStr.isEmpty()) {
                    try {
                        // FIXME: update IBB stage check when IBB is updated
                        FamousBountyStage stage = FamousBountyStage.valueOf(stageStr);
                        if (!SWP_IBBTracker.getTracker().isStageComplete(stage)){
                            log.info("IBB not completed for " + entry.id + " (" + stageStr + ")");
                            proceed = false;
                        }
                    } catch (IllegalArgumentException | NullPointerException | NoClassDefFoundError ex) {
                        log.error("Failed to check IBB completion for " + entry.id, ex);
                        //proceed = false;
                    }
                }
                if (!proceed) continue;
                
                //ignore already bought IBB
                if (!ExerelinConfig.prismRenewBossShips && alreadyBoughtShips.contains(entry.id))
                {
                    log.info("Ship " + entry.id + " already acquired");
                    continue;
                }

                // favour ships from bounties close to the last one we did
                int weight = 3;
                if (entry.ibbNum > 0){
                    int diff = Math.abs(ibbProgress - entry.ibbNum);
                    if (diff > 3) diff = 3;
                    weight += 4*(3 - diff);
                }
                picker.add(entry.id, weight);
            }
        } catch (IOException | JSONException ex) {
            log.error(ex);
        }
        // How many IBB available
        log.info("IBB ships available: " + picker.getItems().size() + ", " + ExerelinConfig.prismNumBossShips);
        for (int i=0; i<ExerelinConfig.prismNumBossShips; i++) {
            if (picker.isEmpty()) break;
            ret.add(picker.pickAndRemove());
        }
        return ret;
    }
    
    public List<String> getAlreadyBoughtShips()
    {
        return new ArrayList<>(alreadyBoughtShips);
    }
    
    public void addAlreadyBoughtShip(String baseHullId)
    {
        alreadyBoughtShips.add(baseHullId);
    }
    
    public void setAlreadyBoughtShips(Collection<String> baseHullIds)
    {
        alreadyBoughtShips.clear();
        alreadyBoughtShips.addAll(baseHullIds);
    }
    
    /**
     * Is this boss ship (as specified in CSV) available given our currently loaded mods?
     * @param factionOrModId
     * @return
     */
    public boolean canLoadShips(String factionOrModId) {
        if (factionOrModId == null) return true;
        if (factionOrModId.equals("ssp")) return ExerelinModPlugin.HAVE_SWP;    // legacy
        return Global.getSector().getFaction(factionOrModId) != null || Global.getSettings().getModManager().isModEnabled(factionOrModId);
    }
    
    //BLACKLISTS
    protected static void setupBlacklists() throws JSONException, IOException {

        // Restricted goods
        restrictedWeapons = new HashSet<>();
        restrictedShips = new HashSet<>();
		restrictedBlueprints = new HashSet<>();
		
        allowedFactions = new HashSet<>(Arrays.asList(new String[] {
            Factions.HEGEMONY, Factions.TRITACHYON, Factions.PERSEAN, Factions.DIKTAT,
            Factions.INDEPENDENT, Factions.LUDDIC_CHURCH, Factions.LUDDIC_PATH, Factions.LIONS_GUARD
        }));
        
        JSONArray factions = Global.getSettings().getMergedSpreadsheetDataForMod("id", 
                FACTION_WHITELIST, ExerelinConstants.MOD_ID);

        for(int i = 0; i < factions.length(); i++) {            
            JSONObject row = factions.getJSONObject(i);
            allowedFactions.add(row.getString("id"));
            log.info("Added to faction whitelist: " + row.getString("id"));
        }
        
        JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod("id",
                WEAPONS_BLACKLIST, ExerelinConstants.MOD_ID);
        for (int x = 0; x < csv.length(); x++)
        {
            JSONObject row = csv.getJSONObject(x);
            restrictedWeapons.add(row.getString("id"));
        }

        // Restricted ships
        csv = Global.getSettings().getMergedSpreadsheetDataForMod("id",
                SHIPS_BLACKLIST, ExerelinConstants.MOD_ID);
        for (int x = 0; x < csv.length(); x++)
        {
            JSONObject row = csv.getJSONObject(x);
            restrictedShips.add(row.getString("id"));
        }
		
		csv = Global.getSettings().getMergedSpreadsheetDataForMod("id",
                BLUEPRINTS_BLACKLIST, ExerelinConstants.MOD_ID);
        for (int x = 0; x < csv.length(); x++)
        {
            JSONObject row = csv.getJSONObject(x);
            restrictedBlueprints.add(row.getString("id"));
        }
    }
    
    /**
     * Called when an ship is present in the player fleet; e.g. if recovered from battle. 
     * So an IBB ship can only be bought or recovered, but not both
     * @param member
     */
    public static void notifyShipAcquired(FleetMemberAPI member)
    {
        notifyShipAcquired(member.getHullSpec().getBaseHullId());
    }
    
    /**
     * Called when an ship is present in the player fleet; e.g. if recovered from battle. 
     * So an IBB ship can only be bought or recovered, but not both
     * @param hullId
     */
    public static void notifyShipAcquired(String hullId)
    {
        cacheSubmarketsIfNeeded();
        for (SubmarketAPI sub : cachedSubmarkets)
        {
            //log.info("Adding ship " + hullId + " to Prism already-bought list");
            PrismMarket prism = (PrismMarket)sub.getPlugin();
            prism.addAlreadyBoughtShip(hullId);
        }
    }
    
    /**
     * Mark all ships in a list as already acquired.
     * So an IBB ship can only be bought or recovered, but not both
     * @param ships
     */
    public static void recordShipsOwned(List<FleetMemberAPI> ships)
    {
        cacheSubmarketsIfNeeded();
        //Global.getSector().getCampaignUI().addMessage("Adding ship " + hullId + " to Prism already-bought list", Color.GREEN);
        for (SubmarketAPI sub : cachedSubmarkets)
        {
            PrismMarket prism = (PrismMarket)sub.getPlugin();
            for (FleetMemberAPI ship : ships)
            {
                //log.info("Adding ship " + ship.getHullSpec().getBaseHullId() + " to Prism already-bought list");
                prism.addAlreadyBoughtShip(ship.getHullSpec().getBaseHullId());
            }
        }
    }
    
    public static void cacheSubmarketsIfNeeded()
    {
        if (cachedSubmarkets == null)
        {
            cachedSubmarkets = new HashSet<>();
            for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
            {
                if (market.hasSubmarket("exerelin_prismMarket"))
                {
                    cachedSubmarkets.add(market.getSubmarket("exerelin_prismMarket"));
                }
            }
        }
    }
    
    // call this on game load
    public static void clearSubmarketCache()
    {
        cachedSubmarkets = null;
    }
	
	public static Set<String> getRestrictedBlueprints() {
		return restrictedBlueprints;
	}
    
    //==========================================================================
    //==========================================================================
    
    @Override
    public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
        if (action == TransferAction.PLAYER_SELL) return true;
        return false;
    }

    @Override
    public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
        if (action == TransferAction.PLAYER_SELL) return true;
        return false;
    }
    
    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
        if (action == TransferAction.PLAYER_SELL) return true;
        return false;
    }
    
    @Override
    public float getTariff() {
        RepLevel level = submarket.getFaction().getRelationshipLevel(Global.getSector().getFaction(Factions.PLAYER));
        float mult = 1f;
        switch (level)
        {
            case NEUTRAL:
                mult = 1f;
                break;
            case FAVORABLE:
                mult = 0.9f;
                break;
            case WELCOMING:
                mult = 0.75f;
                break;
            case FRIENDLY:
                mult = 0.65f;
                break;
            case COOPERATIVE:
                mult = 0.5f;
                break;
            default:
                mult = 1f;
        }
        return mult * ExerelinConfig.prismTariff;
    }
    
    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
        // record purchased ships
        List<ShipSaleInfo> shipsBought = transaction.getShipsBought();
        for (ShipSaleInfo saleInfo : shipsBought)
        {
            String id = saleInfo.getMember().getHullSpec().getBaseHullId();
            if (!alreadyBoughtShips.contains(id))
            {
                //log.info("Purchased boss ship " + hullId + "; will no longer appear");
                alreadyBoughtShips.add(id);
            }
        }
        
        // record purchased fighters
        CargoAPI otherBought = transaction.getBought();
        for (CargoItemQuantity<String> quantity : otherBought.getFighters())
        {
            String id = quantity.getItem();
            if (!alreadyBoughtShips.contains(id))
            {
                //log.info("Purchased boss wing " + id + "; will no longer appear");
                alreadyBoughtShips.add(id);
            }
        }
    }
    
    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        return ILLEGAL_TRANSFER_MESSAGE;
    }
    
    @Override
    public String getIllegalTransferText(CargoStackAPI stack, TransferAction action)
    {
        return ILLEGAL_TRANSFER_MESSAGE;
    }
    
    @Override
    public String getTooltipAppendix(CoreUIAPI ui)
    {
        if (!isEnabled(ui))
        {
            String msg = StringHelper.getString("exerelin_markets", "prismRelTooLow");
            msg = StringHelper.substituteToken(msg, "$faction", submarket.getFaction().getDisplayName());
            msg = StringHelper.substituteToken(msg, "$minRelationship", MIN_STANDING.getDisplayName().toLowerCase());
            return msg;
        }
        return null;
    }
    
    @Override
    public Highlights getTooltipAppendixHighlights(CoreUIAPI ui) {
        String appendix = getTooltipAppendix(ui);
        if (appendix == null) return null;
        
        Highlights h = new Highlights();
        h.setText(appendix);
        h.setColors(Misc.getNegativeHighlightColor());
        return h;
    }
    
    @Override
    public boolean isEnabled(CoreUIAPI ui)
    {
        RepLevel level = submarket.getFaction().getRelationshipLevel(Global.getSector().getFaction(Factions.PLAYER));
        return level.isAtWorst(MIN_STANDING);
    }

    @Override
    public boolean isBlackMarket() {
            return false;
    }
    
    //List IBBs and their progress
    public static class BossShipEntry {
        public String id;
        @Deprecated public int ibbNum;
        public String stage;
        public BossShipEntry(String id, int ibbNum, String stage) {
            this.id = id;
            this.ibbNum = ibbNum;
            this.stage = stage;
        }
    }
}