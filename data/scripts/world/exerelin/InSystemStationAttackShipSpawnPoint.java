package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;

import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.scripts.world.BaseSpawnPoint;

import java.awt.*;
import java.util.*;

@SuppressWarnings("unchecked")
public class InSystemStationAttackShipSpawnPoint extends BaseSpawnPoint
{
	StationRecord stationTarget;
	String fleetOwningFactionId;
	CampaignFleetAPI theFleet;
	boolean boarding = false;
    long lastTimeCheck;

	public InSystemStationAttackShipSpawnPoint(SectorAPI sector, LocationAPI location,
											   float daysInterval, int maxFleets, SectorEntityToken anchor)
	{
		super(sector, location, daysInterval, maxFleets, anchor);
	}

	public void setTarget(StationRecord target)
	{
		if(target != null && stationTarget != null && stationTarget.getStationToken().getFullName().equalsIgnoreCase(target.getStationToken().getFullName()))
			return;

		stationTarget = target;
        boarding = false;
		for(int i = 0; i < this.getFleets().size();i++)
			setFleetAssignments((CampaignFleetAPI)this.getFleets().get(i));
	}

	public void setFaction(String factionId)
	{
		fleetOwningFactionId = factionId;
		while(this.getFleets().size() > 0)
			this.getFleets().remove(0);
	}

	@Override
	public CampaignFleetAPI spawnFleet()
	{
		String type = "exerelinInSystemStationAttackFleet";

		if(this.getFleets().size() == this.getMaxFleets())
			return null;

		if(stationTarget == null)
			return null;

		boarding = false;

		CampaignFleetAPI fleet = getSector().createFleet(fleetOwningFactionId, type);

	    DiplomacyRecord diplomacyRecord = ExerelinData.getInstance().getSectorManager().getDiplomacyManager().getRecordForFaction(fleetOwningFactionId);
	    if (diplomacyRecord.hasWarTargetInSystem((StarSystemAPI)getLocation(), false))
	      ExerelinUtils.addRandomEscortShipsToFleet (fleet, 3, 5, fleetOwningFactionId, getSector());
	    else
	      ExerelinUtils.addRandomEscortShipsToFleet (fleet, 1, 2, fleetOwningFactionId, getSector());

		if(ExerelinUtils.canStationSpawnFleet(getAnchor(), fleet, 1, 0.8f, false, ExerelinUtils.getCrewXPLevelForFaction(this.fleetOwningFactionId)))
		{
			getLocation().spawnFleet(getAnchor(), 0, 0, fleet);
			theFleet = fleet;
			fleet.setPreferredResupplyLocation(getAnchor());
            fleet.setName("Boarding Fleet");

			setFleetAssignments(fleet);

			this.getFleets().add(fleet);
			return fleet;
		}
		else
			return null;
	}

	private void setFleetAssignments(CampaignFleetAPI fleet)
	{
		fleet.clearAssignments();
		if(stationTarget != null && ExerelinUtils.isValidBoardingFleet(fleet, true))
		{
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, stationTarget.getStationToken(), 3000, createTestTargetScript());
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, stationTarget.getStationToken(), 3, createArrivedScript());
            fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, stationTarget.getStationToken(), 10);
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, stationTarget.getStationToken(), 10);
		}
		else
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 10);
	}

	private Script createTestTargetScript() {
		return new Script() {
			public void run() {
				if(stationTarget != null && stationTarget.getOwner() != null)
				{
					if(stationTarget.getOwner().getFactionId().equalsIgnoreCase(fleetOwningFactionId))
					{
						// If we own station then just go there
						return; // Will run arrived script
					}
					else if(stationTarget.getOwner().getGameRelationship(fleetOwningFactionId) >= 0)
					{
						// If neutral/ally owns station, head home (home station may reassign a target later)
						theFleet.clearAssignments();
                        boarding = false;
						theFleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 10);
						return;
					}
					else if(!boarding && stationTarget.getOwner().getFactionId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
					{
						System.out.println("Player owned " + stationTarget.getStationToken().getFullName() + " being boarded by " + fleetOwningFactionId);
						Global.getSector().addMessage(stationTarget.getStationToken().getFullName() + " is being boarded by " + fleetOwningFactionId, Color.magenta);
					}
				}

                if(!boarding)
                {
                    lastTimeCheck = Global.getSector().getClock().getTimestamp();
                    boarding = true;
                }

                if(Global.getSector().getClock().getElapsedDaysSince(lastTimeCheck) >= 1)
                {
                    lastTimeCheck = Global.getSector().getClock().getTimestamp();
                    if(ExerelinUtils.boardStationAttempt(theFleet, stationTarget.getStationToken(), false))
                    {
                        boarding = false;
                        return;
                    }
                    else
                        setFleetAssignments(theFleet);
                }
                else
                    setFleetAssignments(theFleet);

			}
		};
	}

	private Script createArrivedScript() {
		return new Script() {
			public void run() {
				if(stationTarget.getOwner() != null && stationTarget.getOwner().getFactionId().equalsIgnoreCase(fleetOwningFactionId))
				{
					// If we already own it deliver resources (as if we took it over), defend and despawn
					CargoAPI cargo = stationTarget.getStationToken().getCargo();
					cargo.addCrew(CargoAPI.CrewXPLevel.REGULAR,  80);
					cargo.addFuel(80);
					cargo.addMarines(40);
					cargo.addSupplies(320);
                    ExerelinUtils.removeShipsFromFleet(theFleet, ExerelinData.getInstance().getValidBoardingFlagships(), true);
                    ExerelinUtils.removeShipsFromFleet(theFleet, ExerelinData.getInstance().getValidTroopTransportShips(), false);
                    ExerelinUtils.resetFleetCargoToDefaults(theFleet, 0.5f, 0.1f, ExerelinUtils.getCrewXPLevelForFaction(fleetOwningFactionId));
				}
				else if(stationTarget.getOwner() != null && stationTarget.getOwner().getGameRelationship(fleetOwningFactionId) >= 0)
				{
					// If neutral/ally owns station, go home
					theFleet.clearAssignments();
                    boarding = false;
					theFleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 10);
				}
				else
				{
					// Else, take over station
					stationTarget.setOwner(theFleet.getFaction().getId(), true, true);
					stationTarget.clearCargo();
                    ExerelinUtils.removeShipsFromFleet(theFleet, ExerelinData.getInstance().getValidTroopTransportShips(), false);
                    ExerelinUtils.resetFleetCargoToDefaults(theFleet, 0.5f, 0.1f, ExerelinUtils.getCrewXPLevelForFaction(fleetOwningFactionId));
				}
			}
		};
	}
}






