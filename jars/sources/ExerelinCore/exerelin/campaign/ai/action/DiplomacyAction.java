package exerelin.campaign.ai.action;

import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.intel.diplomacy.DiplomacyIntel;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;

public class DiplomacyAction extends BaseStrategicAction {

    public DiplomacyIntel getIntel() {
        return (DiplomacyIntel)delegate;
    }

    @Override
    public boolean generate() {
        boolean canPositive = concern.getDef().hasTag("diplomacy_positive");
        boolean canNegative = concern.getDef().hasTag("diplomacy_negative");

        if (!canNegative && !canPositive) {
            canPositive = true;
            canNegative = true;
        }

        DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(ai.getFactionId());
        float disp = brain.getDisposition(concern.getFaction().getId()).disposition.getModifiedValue();
        float ourStrength = DiplomacyBrain.getFactionStrength(ai.getFactionId());
        float theirStrength = DiplomacyBrain.getFactionStrength(concern.getFaction().getId());

        if (ourStrength*1.5f < theirStrength)
        {
            canNegative = false;
        }
        if (disp <= DiplomacyBrain.DISLIKE_THRESHOLD)
        {
            canPositive = false;
        }
        else if (disp >= DiplomacyBrain.LIKE_THRESHOLD)
        {
            canNegative = false;
        }

        if (!canNegative && !canPositive) return false;

        DiplomacyManager.DiplomacyEventParams params = new DiplomacyManager.DiplomacyEventParams();
        params.random = false;
        params.onlyPositive = !canNegative;
        params.onlyNegative = !canPositive;

        delegate = DiplomacyManager.createDiplomacyEvent(concern.getFaction(), ai.getFaction(), null, params);
        return delegate != null;
    }

    @Override
    public void applyPriorityModifiers() {
        super.applyPriorityModifiers();
        boolean canPositive = concern.getDef().hasTag("diplomacy_positive");
        boolean canNegative = concern.getDef().hasTag("diplomacy_negative");
        float diplomacyChanceAdjustment = 1;
        boolean positive;
        if (canPositive && !canNegative) {
            positive = true;
        } else if (!canPositive && canNegative) {
            positive = false;
        } else return;
        diplomacyChanceAdjustment = getFactionDiplomacyChance(!positive, concern.getFaction().getId());
        priority.modifyMult("diplomacyChance", diplomacyChanceAdjustment,
                StrategicAI.getString("statDiplomacyChance" + (positive ? "Positive" : "Negative"), true));
    }

    public float getFactionDiplomacyChance(boolean isNegative, String otherFactionId) {
        String factionId = ai.getFactionId();
        float chance = 1;
        if (DiplomacyManager.getManager().getStartRelationsMode().isDefault()) {
            if (isNegative) {
                float mult = NexFactionConfig.getDiplomacyNegativeChance(factionId, otherFactionId);
                chance *= mult;
            }
            else
            {
                float mult = NexFactionConfig.getDiplomacyPositiveChance(factionId, otherFactionId);
                chance *= mult;
            }

            float dominance = Math.max( DiplomacyManager.getDominanceFactor(factionId), DiplomacyManager.getDominanceFactor(otherFactionId) );
            if (dominance > DiplomacyManager.DOMINANCE_MIN)
            {
                float strength = (dominance - DiplomacyManager.DOMINANCE_MIN)/(1 - DiplomacyManager.DOMINANCE_MIN);
                if (isNegative) chance += (DiplomacyManager.DOMINANCE_DIPLOMACY_NEGATIVE_EVENT_MOD * strength);
                else chance += (DiplomacyManager.DOMINANCE_DIPLOMACY_POSITIVE_EVENT_MOD * strength);
            }
        }
        return chance;
    }

    @Override
    public void init() {
        super.init();
        // used to be in generate() directly but putting it here makes some stuff technically cleaner
        end(StrategicActionDelegate.ActionStatus.SUCCESS);
    }

    @Override
    public String getName() {
        if (getIntel() != null) return getIntel().getSmallDescriptionTitle();
        return getDef().id;
    }

    @Override
    public String getIcon() {
        if (getIntel() != null)  return getIntel().getIcon();
        return null;
    }

    @Override
    public boolean canUseForConcern(StrategicConcern concern) {
        if (concern.getDef().hasTag("diplomacy_positive") && !this.getDef().hasTag("friendly"))
            return false;

        if (concern.getDef().hasTag("diplomacy_negative") && !this.getDef().hasTag("unfriendly"))
            return false;

        return concern.getDef().hasTag("canDiplomacy");
    }

    @Override
    public boolean isValid() {
        return !NexConfig.getFactionConfig(ai.getFactionId()).disableDiplomacy;
    }
}