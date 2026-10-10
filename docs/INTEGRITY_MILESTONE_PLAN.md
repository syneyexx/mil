# Integrity milestone plan (persistence / trade / migration / colonization)

**Baseline HEAD:** `30497f2a792bbee39869b70f86a183751dc7325d` (mod 9.0.2, MC 1.21.1, NeoForge 21.1.256, Java 21)  
**Owner policy for this run:** no GameTests, no GitHub CI test workflow, no agent-run verification — owner tests locally.

## Ownership map

| Concern | Owner | Persistence |
|---------|-------|-------------|
| Trade payment / escrow stacks | `SettlementTradeOperation` | `millenaire_civilization` |
| Physical delivery / compensation | `SettlementTradeExecutor` + `BuildingInventory` | village inventories + chunk BEs |
| Economic booking / events | `finalizeSuccessfulTransfer` | economy SD + published keys |
| Migration location | `Village` records + entity | `millenaire_villages` + entity |
| Migration op status | `MigrationOperation` | civilization SD |
| Colonization escrow / refund | `ColonizationOperation` | `millenaire_colonization` |
| Physical colony blocks | `ChunkSettlementMaterializer` | chunk files |
| Activation | `SettlementActivator` | villages + plan SD |

SavedData files and chunk files are **not** one atomic transaction. Guarantee: reliable in-memory execution, consistent save/load of exact stacks, bounded recovery / hard block on ambiguous mid-phases. No general exactly-once crash claim.

## Invariants

1. Trade: `withdrawn ≡ delivered + compensated + pendingDelivery + pendingCompensation` (by concrete stack identity). Historical withdrawn are evidence, not second stock.
2. Save/load preserves stacks, counts, components (not `Map<Item,Integer>` only).
3. COMPLETE only after delivery/compensation **and** booking/event finalization.
4. Migration recovery yields one village ownership; clear `needsRecovery` only after postconditions.
5. Colonization: no physical mutation / activation until full founding costs covered; one stack cannot cover two cost lines; no unused-cost refund after build started.

## Changeset A — trade persistence + finalization

**Problem:** `CivilizationSavedData.factory` drops `Provider` → `SettlementTradeOperation.load(..., null)` → Item codec stacks vanish; finalization-only ops skipped by `needsWork()`; recovery day key drifts.

**Files:**  
`CivilizationSavedData.java`, `SettlementTradeOperation.java`, `SettlementTradeExecutor.java`, new `org.millenaire.util.ExactStackCodec`, reuse from `ColonizationOperation`.

**Postconditions:** Factory round-trip keeps components; decode failures keep unresolved raw NBT; legacy id/Count migrates without inventing Count=0; finalization-only recovery books once; no second delivery on finalize retry.

## Changeset B — migration reconciliation

**Problem:** `recoverPending` ignores `trySetStatus` false; clears `needsRecovery` anyway; skips persisted `RESERVED`.

**Files:** `MigrationOperation.java`, `MigrationTransactionService.java`.

**Postconditions:** dest-only → COMPLETE via reconcile path; origin-only → safe FAILED; both → dest wins + origin cleanup; neither/missing village → keep unresolved evidence; repeated recover is no-op when already consistent.

## Changeset C — founding costs + worldgen gate

**Problem:** unpaid / partial / FAILED ops can still materialize; `resourcesReserved`+nonempty treated as paid; duplicated cost math; `costsConsumed` on plan status not first block.

**Files:** new `FoundingCostService`, `ColonizationService`, `CivilizationActionService`, `CivilizationPresentationService`, `SettlementPlan` (+op link), `WorldgenEngine` / `ChunkSettlementMaterializer` / `SettlementActivator`, `ColonizationOperation` stack codec reuse.

**Postconditions:** all physical colonization entries share one full-coverage gate; partial escrow visible but not authorizing; consumption on first real mutation; pre-build fail refunds exact stacks; post-build no full unused refund.

## Explicitly out of scope (backlog)

- Nested exporter/faction/inventory scan budgeting + activation index  
- `VillageRequest.recoveryOutstanding` real recovery  
- VillageBook snapshot/map rate limiting  
- Published-key dedupe store with per-entry `expiresAt`  

## Verification (owner-local; not run by agent)

```bash
./gradlew build
# owner: GameTests / dedicated-server smoke as desired
```
