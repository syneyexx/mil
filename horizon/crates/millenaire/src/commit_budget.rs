// Exact port of org.millenaire.perf.plan.CommitBudget
pub const DEFAULT_UNITS_PER_TICK: i32 = 8;

#[derive(Debug)]
pub struct CommitBudget {
    remaining: i32,
    candidates_remaining: i32,
}

impl Default for CommitBudget {
    fn default() -> Self {
        Self {
            remaining: 0,
            candidates_remaining: 0,
        }
    }
}

impl CommitBudget {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn reset_tick(&mut self, commit_units: i32, candidate_units: i32) {
        self.remaining = commit_units.max(0);
        self.candidates_remaining = candidate_units.max(0);
    }

    pub fn try_spend_commit(&mut self, units: i32) -> bool {
        if units <= 0 {
            return true;
        }
        if self.remaining < units {
            return false;
        }
        self.remaining -= units;
        true
    }

    pub fn try_spend_candidate(&mut self, units: i32) -> bool {
        if units <= 0 {
            return true;
        }
        if self.candidates_remaining < units {
            return false;
        }
        self.candidates_remaining -= units;
        true
    }

    pub fn remaining_commits(&self) -> i32 {
        self.remaining
    }

    pub fn remaining_candidates(&self) -> i32 {
        self.candidates_remaining
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn spend_respects_budget() {
        let mut b = CommitBudget::new();
        b.reset_tick(8, 4);
        assert!(b.try_spend_commit(3));
        assert_eq!(b.remaining_commits(), 5);
        assert!(!b.try_spend_commit(6));
        assert_eq!(b.remaining_commits(), 5);
        assert!(b.try_spend_candidate(4));
        assert!(!b.try_spend_candidate(1));
    }
}
