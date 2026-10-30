// GoalRegistry — exact goal id set from org.millenaire.goal.impl (+ builtins).
use std::collections::BTreeSet;

pub const BUILTIN_GOAL_IDS: &[&str] = &[
    "IdleGoal",
    "RestGoal",
    "BuildGoal",
    "SocialiseGoal",
    "ChatGoal",
    "SellerGoal",
    "ChildBecomeAdultGoal",
    "BringBackHomeGoal",
    "DeliverResourcesToShopGoal",
    "GetResourcesForShopsGoal",
    "GetGoodsForHouseholdGoal",
    "GetToolGoal",
    "GatherGoodsGoal",
    "LightHearthGoal",
    "HuntMonsterGoal",
    "ActivityMissionGoal",
    "RaidVillageGoal",
    "DefendVillageGoal",
    "BuildPathGoal",
    "ClearOldPathGoal",
    "EngageTargetGoal",
    "ForeignMerchantKeepStallGoal",
    "HideGoal",
    "HiredEscortGoal",
    "HostileVillagerGoal",
    "InvestigateChestGoal",
    "MerchantVisitBuildingGoal",
    "MerchantVisitInnGoal",
    "ObserveVillagerGoal",
    "PlayGoal",
    "RitualAttendanceTask",
    "SpotGatheringGoal",
    "VisitBuildingGoal",
    "WantedPlayerGoal",
    "WarEscortGoal",
    "WarSabotageGoal"
];

#[derive(Clone, Debug, Default)]
pub struct GoalRegistry {
    ids: BTreeSet<String>,
}

impl GoalRegistry {
    pub fn with_builtins() -> Self {
        let mut g = Self::default();
        for id in BUILTIN_GOAL_IDS {
            g.register(*id);
        }
        g
    }

    pub fn register(&mut self, id: impl Into<String>) {
        self.ids.insert(id.into());
    }

    pub fn contains(&self, id: &str) -> bool {
        self.ids.contains(id)
    }

    pub fn len(&self) -> usize {
        self.ids.len()
    }

    pub fn is_empty(&self) -> bool {
        self.ids.is_empty()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn builtins_match_mil_freeze_list() {
        let g = GoalRegistry::with_builtins();
        assert!(g.contains("IdleGoal"));
        assert!(g.contains("ActivityMissionGoal"));
        assert!(g.contains("RaidVillageGoal"));
        assert!(g.len() >= 30);
    }
}
