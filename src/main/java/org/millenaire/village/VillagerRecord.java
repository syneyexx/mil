package org.millenaire.village;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.millenaire.building.BuildingId;
import org.millenaire.combat.MilitaryStrength;
import org.millenaire.culture.ModCultures;
import org.millenaire.culture.VillagerType;
import org.millenaire.entity.MillVillager;
import org.millenaire.entity.ModelType;
import org.millenaire.entity.VillagerAppearanceFactory;
import org.millenaire.entity.VillagerIdentity;
import org.millenaire.entity.VillagerInventory;
import org.slf4j.Logger;

public class VillagerRecord {
   private static final Logger LOGGER = LogUtils.getLogger();
   private UUID uuid;
   private ResourceLocation villagerTypeId;
   @Nullable
   private BuildingId homeBuilding;
   private VillagerIdentity identity = new VillagerIdentity();
   private final VillagerInventory inventory = new VillagerInventory();
   private boolean killed;
   private long lastRespawnTick;
   @Nullable
   private BlockPos lastKnownPos;
   private int visitorNbNights;
   private boolean innTradingAnnounced;
   private boolean awayRaiding;
   private boolean raidingVillage;
   @Nullable
   private VillageId originalVillageId;
   private long raiderSpawn;
   private boolean awayHired;
   @Nullable
   private UUID hiredBy;
   private long hiredUntil;
   private boolean aggressiveStance;
   /** Exclusive regional-activity reservation; null when free. */
   @Nullable
   private UUID activityOperationId;
   private final List<String> questTags = new ArrayList<>();
   @Nullable
   private UUID faithInstanceId;
   @Nullable
   private UUID orderChapterId;
   @Nullable
   private org.millenaire.faith.OrderRole orderRole;
   private int devotion;
   @Nullable
   private UUID householdId;
   @Nullable
   private UUID spouseUuid;
   @Nullable
   private UUID motherUuid;
   @Nullable
   private UUID fatherUuid;
   @Nullable
   private UUID mentorUuid;
   private final List<UUID> apprenticeUuids = new ArrayList<>();
   private final Map<String, Integer> craftXp = new LinkedHashMap<>();
   private final List<String> memories = new ArrayList<>();
   private static final int MAX_APPRENTICES = 2;
   private static final int MAX_CRAFT_SKILLS = 6;
   private static final int MAX_MEMORIES = 8;
   private static final int MAX_CRAFT_XP = 1000;

   public VillagerRecord(UUID uuid, ResourceLocation villagerTypeId, @Nullable BuildingId homeBuilding) {
      this.uuid = uuid;
      this.villagerTypeId = villagerTypeId;
      this.homeBuilding = homeBuilding;
   }

   public UUID getUuid() {
      return this.uuid;
   }

   public void setUuid(UUID uuid) {
      this.uuid = uuid;
   }

   @Nullable
   public ResourceLocation getVillagerTypeId() {
      return this.villagerTypeId;
   }

   public void setVillagerTypeId(ResourceLocation villagerTypeId) {
      this.villagerTypeId = villagerTypeId;
   }

   @Nullable
   public BuildingId getHomeBuilding() {
      return this.homeBuilding;
   }

   public void setHomeBuilding(@Nullable BuildingId homeBuilding) {
      this.homeBuilding = homeBuilding;
   }

   public VillagerIdentity getIdentity() {
      return this.identity;
   }

   public String getFirstName() {
      return this.identity.getFirstName();
   }

   public String getFamilyName() {
      return this.identity.getFamilyName();
   }

   public String getRoleName() {
      return this.identity.getRoleName();
   }

   public String getFathersName() {
      return this.identity.getFathersName();
   }

   public String getMothersName() {
      return this.identity.getMothersName();
   }

   public String getSpousesName() {
      return this.identity.getSpousesName();
   }

   public String getMaidenName() {
      return this.identity.getMaidenName();
   }

   @Nullable
   public ResourceLocation getTexture() {
      return this.identity.getTexture();
   }

   @Nullable
   public ResourceLocation getClothTexture0() {
      return this.identity.getClothTexture0();
   }

   @Nullable
   public ResourceLocation getClothTexture1() {
      return this.identity.getClothTexture1();
   }

   public float getVillagerScale() {
      return this.identity.getVillagerScale();
   }

   public ModelType getModelType() {
      return this.identity.getModelType();
   }

   public int getChildSize() {
      return this.identity.getChildSize();
   }

   public VillagerInventory getInventory() {
      return this.inventory;
   }

   public boolean isKilled() {
      return this.killed;
   }

   public void setKilled(boolean killed) {
      this.killed = killed;
   }

   public long getLastRespawnTick() {
      return this.lastRespawnTick;
   }

   public void setLastRespawnTick(long lastRespawnTick) {
      this.lastRespawnTick = lastRespawnTick;
   }

   @Nullable
   public BlockPos getLastKnownPos() {
      return this.lastKnownPos;
   }

   public int getVisitorNbNights() {
      return this.visitorNbNights;
   }

   public void setVisitorNbNights(int visitorNbNights) {
      this.visitorNbNights = visitorNbNights;
   }

   public boolean isInnTradingAnnounced() {
      return this.innTradingAnnounced;
   }

   public void setInnTradingAnnounced(boolean innTradingAnnounced) {
      this.innTradingAnnounced = innTradingAnnounced;
   }

   public boolean isAwayRaiding() {
      return this.awayRaiding;
   }

   public void setAwayRaiding(boolean awayRaiding) {
      this.awayRaiding = awayRaiding;
   }

   public boolean isRaidingVillage() {
      return this.raidingVillage;
   }

   public void setRaidingVillage(boolean raidingVillage) {
      this.raidingVillage = raidingVillage;
   }

   @Nullable
   public VillageId getOriginalVillageId() {
      return this.originalVillageId;
   }

   public void setOriginalVillageId(@Nullable VillageId originalVillageId) {
      this.originalVillageId = originalVillageId;
   }

   public long getRaiderSpawn() {
      return this.raiderSpawn;
   }

   public void setRaiderSpawn(long raiderSpawn) {
      this.raiderSpawn = raiderSpawn;
   }

   public boolean isAwayHired() {
      return this.awayHired;
   }

   @Nullable
   public UUID getHiredBy() {
      return this.hiredBy;
   }

   public long getHiredUntil() {
      return this.hiredUntil;
   }

   public boolean isAggressiveStance() {
      return this.aggressiveStance;
   }

   public void setAggressiveStance(boolean aggressive) {
      this.aggressiveStance = aggressive;
   }

   public void setHireState(@Nullable UUID owner, long until) {
      this.hiredBy = owner;
      this.hiredUntil = until;
      this.awayHired = owner != null;
      if (owner == null) {
         this.aggressiveStance = false;
      }
   }

   @Nullable
   public UUID getActivityOperationId() {
      return this.activityOperationId;
   }

   public void setActivityOperationId(@Nullable UUID activityOperationId) {
      this.activityOperationId = activityOperationId;
   }

   public int getMilitaryStrength() {
      if (this.villagerTypeId == null) {
         return 0;
      }

      VillagerType type = ModCultures.getVillagerType(this.villagerTypeId);
      if (type == null) {
         return 0;
      }

      MilitaryStrength.InventoryScan scan = MilitaryStrength.scan(this.inventory);
      boolean archerWithBow = type.isArcher() && scan.hasBow();
      return MilitaryStrength.compute((int)type.maxHealth(), type.baseAttackStrength(), scan.bestMeleeWeaponDamage(), archerWithBow, scan.totalArmorValue());
   }

   public List<String> getQuestTags() {
      return Collections.unmodifiableList(this.questTags);
   }

   public void addQuestTag(String tag) {
      if (!this.questTags.contains(tag)) {
         this.questTags.add(tag);
      }
   }

   public void removeQuestTag(String tag) {
      this.questTags.remove(tag);
   }

   public boolean hasQuestTag(String tag) {
      return this.questTags.contains(tag);
   }

   @Nullable
   public UUID getFaithInstanceId() {
      return this.faithInstanceId;
   }

   public void setFaithInstanceId(@Nullable UUID faithInstanceId) {
      this.faithInstanceId = faithInstanceId;
   }

   @Nullable
   public UUID getOrderChapterId() {
      return this.orderChapterId;
   }

   public void setOrderChapterId(@Nullable UUID orderChapterId) {
      this.orderChapterId = orderChapterId;
   }

   @Nullable
   public org.millenaire.faith.OrderRole getOrderRole() {
      return this.orderRole;
   }

   public void setOrderRole(@Nullable org.millenaire.faith.OrderRole orderRole) {
      this.orderRole = orderRole;
   }

   public int getDevotion() {
      return this.devotion;
   }

   public void setDevotion(int devotion) {
      this.devotion = Math.max(0, Math.min(100, devotion));
   }

   @Nullable
   public UUID getHouseholdId() {
      return this.householdId;
   }

   public void setHouseholdId(@Nullable UUID householdId) {
      this.householdId = householdId;
   }

   @Nullable
   public UUID getSpouseUuid() {
      return this.spouseUuid;
   }

   public void setSpouseUuid(@Nullable UUID spouseUuid) {
      this.spouseUuid = spouseUuid;
   }

   @Nullable
   public UUID getMotherUuid() {
      return this.motherUuid;
   }

   public void setMotherUuid(@Nullable UUID motherUuid) {
      this.motherUuid = motherUuid;
   }

   @Nullable
   public UUID getFatherUuid() {
      return this.fatherUuid;
   }

   public void setFatherUuid(@Nullable UUID fatherUuid) {
      this.fatherUuid = fatherUuid;
   }

   @Nullable
   public UUID getMentorUuid() {
      return this.mentorUuid;
   }

   public void setMentorUuid(@Nullable UUID mentorUuid) {
      this.mentorUuid = mentorUuid;
   }

   public List<UUID> getApprenticeUuids() {
      return Collections.unmodifiableList(this.apprenticeUuids);
   }

   public boolean addApprentice(UUID apprentice) {
      if (apprentice == null || this.apprenticeUuids.contains(apprentice) || this.apprenticeUuids.size() >= MAX_APPRENTICES) {
         return false;
      }
      this.apprenticeUuids.add(apprentice);
      return true;
   }

   public Map<String, Integer> getCraftXp() {
      return Collections.unmodifiableMap(this.craftXp);
   }

   public int getCraftXp(String skill) {
      return this.craftXp.getOrDefault(skill, 0);
   }

   public void addCraftXp(String skill, int amount) {
      if (skill == null || skill.isEmpty() || amount <= 0) {
         return;
      }
      int next = Math.min(MAX_CRAFT_XP, this.craftXp.getOrDefault(skill, 0) + amount);
      this.craftXp.put(skill, next);
      while (this.craftXp.size() > MAX_CRAFT_SKILLS) {
         String lowest = null;
         int lowestXp = Integer.MAX_VALUE;
         for (Entry<String, Integer> e : this.craftXp.entrySet()) {
            if (e.getValue() < lowestXp) {
               lowestXp = e.getValue();
               lowest = e.getKey();
            }
         }
         if (lowest == null) {
            break;
         }
         this.craftXp.remove(lowest);
      }
   }

   public List<String> getMemories() {
      return Collections.unmodifiableList(this.memories);
   }

   public void addMemory(String memory) {
      if (memory == null || memory.isEmpty()) {
         return;
      }
      this.memories.add(memory);
      while (this.memories.size() > MAX_MEMORIES) {
         this.memories.removeFirst();
      }
   }

   public void updateFromEntity(MillVillager villager) {
      this.villagerTypeId = villager.getVillagerTypeId();
      this.homeBuilding = villager.getHomeBuilding();
      this.identity = new VillagerIdentity(villager.getIdentity());
      this.lastKnownPos = villager.blockPosition();
      this.visitorNbNights = villager.getVisitorNbNights();
      this.setHireState(villager.getHiredBy(), villager.getHiredUntil());
      this.setAggressiveStance(villager.isAggressiveStance());
      this.inventory.clear();

      for (Entry<Item, Integer> entry : villager.getInventory().getAll().entrySet()) {
         this.inventory.add(entry.getKey(), entry.getValue());
      }
   }

   public void applyToEntity(MillVillager villager) {
      villager.setVillagerTypeId(this.villagerTypeId);
      villager.setHomeBuilding(this.homeBuilding);
      if (VillagerAppearanceFactory.isCorruptedName(this.identity.getFirstName()) && this.villagerTypeId != null) {
         VillagerType vType = ModCultures.getVillagerType(this.villagerTypeId);
         if (vType != null) {
            String[] names = VillagerAppearanceFactory.generateName(vType);
            if (!VillagerAppearanceFactory.isCorruptedName(names[0])) {
               this.identity.setFirstName(names[0]);
               this.identity.setFamilyName(names[1]);
            }
         }
      }

      villager.initAppearance(
         this.identity.getModelType(),
         this.identity.getTexture(),
         this.identity.getClothTexture0(),
         this.identity.getClothTexture1(),
         this.identity.getVillagerScale(),
         this.identity.getFirstName(),
         this.identity.getFamilyName(),
         this.identity.getRoleName()
      );
      villager.setFathersName(this.identity.getFathersName());
      villager.setMothersName(this.identity.getMothersName());
      villager.setSpousesName(this.identity.getSpousesName());
      villager.setMaidenName(this.identity.getMaidenName());
      if (this.identity.getChildSize() >= 0) {
         villager.setChildSize(this.identity.getChildSize());
      }

      villager.getInventory().clear();

      for (Entry<Item, Integer> entry : this.inventory.getAll().entrySet()) {
         villager.getInventory().add(entry.getKey(), entry.getValue());
      }

      villager.getIdentity().setClothName(this.identity.getClothName());
      VillagerType vType = ModCultures.getVillagerType(this.villagerTypeId);
      if (vType != null) {
         villager.updateClothTextures(vType);
      }

      villager.setVisitorNbNights(this.visitorNbNights);
      villager.setHireState(this.hiredBy, this.hiredUntil);
      villager.setAggressiveStance(this.aggressiveStance);
   }

   public void copyAppearanceAndInventoryFrom(VillagerRecord source) {
      this.identity = new VillagerIdentity(source.identity);
      this.inventory.clear();

      for (Entry<Item, Integer> entry : source.inventory.getAll().entrySet()) {
         this.inventory.add(entry.getKey(), entry.getValue());
      }
   }

   public void save(CompoundTag tag) {
      tag.putUUID("uuid", this.uuid);
      tag.putString("type", this.villagerTypeId.toString());
      if (this.homeBuilding != null) {
         tag.putUUID("home", this.homeBuilding.uuid());
      }

      this.identity.save(tag);
      tag.putBoolean("killed", this.killed);
      tag.putLong("lastRespawnTick", this.lastRespawnTick);
      if (this.lastKnownPos != null) {
         tag.putIntArray("last_known_pos", new int[]{this.lastKnownPos.getX(), this.lastKnownPos.getY(), this.lastKnownPos.getZ()});
      }

      tag.putInt("visitor_nb_nights", this.visitorNbNights);
      if (this.innTradingAnnounced) {
         tag.putBoolean("inn_trading_announced", true);
      }

      if (this.awayRaiding) {
         tag.putBoolean("away_raiding", true);
      }

      if (this.raidingVillage) {
         tag.putBoolean("raiding_village", true);
      }

      if (this.originalVillageId != null) {
         tag.putUUID("original_village_id", this.originalVillageId.uuid());
      }

      if (this.raiderSpawn != 0L) {
         tag.putLong("raider_spawn", this.raiderSpawn);
      }

      tag.putBoolean("away_hired", this.awayHired);
      tag.putBoolean("hire_stance", this.aggressiveStance);
      if (this.hiredBy != null) {
         tag.putUUID("hired_by", this.hiredBy);
         tag.putLong("hired_until", this.hiredUntil);
      }
      if (this.activityOperationId != null) {
         tag.putUUID("activity_operation_id", this.activityOperationId);
      }

      CompoundTag invTag = new CompoundTag();
      this.inventory.save(invTag);
      tag.put("inventory", invTag);
      if (!this.questTags.isEmpty()) {
         ListTag tagList = new ListTag();

         for (String qt : this.questTags) {
            tagList.add(StringTag.valueOf(qt));
         }

         tag.put("questTags", tagList);
      }
      if (this.faithInstanceId != null) {
         tag.putUUID("faith_instance_id", this.faithInstanceId);
      }
      if (this.orderChapterId != null) {
         tag.putUUID("order_chapter_id", this.orderChapterId);
      }
      if (this.orderRole != null) {
         tag.putString("order_role", this.orderRole.name());
      }
      if (this.devotion > 0) {
         tag.putInt("devotion", this.devotion);
      }
      if (this.householdId != null) {
         tag.putUUID("household_id", this.householdId);
      }
      if (this.spouseUuid != null) {
         tag.putUUID("spouse_uuid", this.spouseUuid);
      }
      if (this.motherUuid != null) {
         tag.putUUID("mother_uuid", this.motherUuid);
      }
      if (this.fatherUuid != null) {
         tag.putUUID("father_uuid", this.fatherUuid);
      }
      if (this.mentorUuid != null) {
         tag.putUUID("mentor_uuid", this.mentorUuid);
      }
      if (!this.apprenticeUuids.isEmpty()) {
         ListTag apps = new ListTag();
         for (UUID id : this.apprenticeUuids) {
            CompoundTag a = new CompoundTag();
            a.putUUID("id", id);
            apps.add(a);
         }
         tag.put("apprentices", apps);
      }
      if (!this.craftXp.isEmpty()) {
         CompoundTag xpTag = new CompoundTag();
         for (Entry<String, Integer> e : this.craftXp.entrySet()) {
            xpTag.putInt(e.getKey(), e.getValue());
         }
         tag.put("craft_xp", xpTag);
      }
      if (!this.memories.isEmpty()) {
         ListTag mem = new ListTag();
         for (String m : this.memories) {
            mem.add(StringTag.valueOf(m));
         }
         tag.put("memories", mem);
      }
   }

   @Nullable
   public static VillagerRecord load(CompoundTag tag) {
      try {
         UUID uuid = tag.getUUID("uuid");
         ResourceLocation typeId = ResourceLocation.parse(tag.getString("type"));
         BuildingId home = tag.hasUUID("home") ? new BuildingId(tag.getUUID("home")) : null;
         VillagerRecord record = new VillagerRecord(uuid, typeId, home);
         record.identity = VillagerIdentity.load(tag);
         record.killed = tag.getBoolean("killed");
         record.lastRespawnTick = tag.getLong("lastRespawnTick");
         if (tag.contains("last_known_pos")) {
            int[] pos = tag.getIntArray("last_known_pos");
            if (pos.length == 3) {
               record.lastKnownPos = new BlockPos(pos[0], pos[1], pos[2]);
            }
         }

         record.visitorNbNights = tag.getInt("visitor_nb_nights");
         record.innTradingAnnounced = tag.getBoolean("inn_trading_announced");
         record.awayRaiding = tag.getBoolean("away_raiding");
         record.raidingVillage = tag.getBoolean("raiding_village");
         if (tag.hasUUID("original_village_id")) {
            record.originalVillageId = new VillageId(tag.getUUID("original_village_id"));
         }

         record.raiderSpawn = tag.getLong("raider_spawn");
         record.awayHired = tag.getBoolean("away_hired");
         record.aggressiveStance = tag.getBoolean("hire_stance");
         if (tag.hasUUID("hired_by")) {
            record.hiredBy = tag.getUUID("hired_by");
            record.hiredUntil = tag.getLong("hired_until");
         }
         if (tag.hasUUID("activity_operation_id")) {
            record.activityOperationId = tag.getUUID("activity_operation_id");
         }

         if (tag.contains("inventory")) {
            record.inventory.load(tag.getCompound("inventory"));
         }

         if (tag.contains("questTags")) {
            ListTag tagList = tag.getList("questTags", 8);

            for (int i = 0; i < tagList.size(); i++) {
               record.questTags.add(tagList.getString(i));
            }
         }
         if (tag.hasUUID("faith_instance_id")) {
            record.faithInstanceId = tag.getUUID("faith_instance_id");
         }
         if (tag.hasUUID("order_chapter_id")) {
            record.orderChapterId = tag.getUUID("order_chapter_id");
         }
         if (tag.contains("order_role")) {
            record.orderRole = org.millenaire.faith.OrderRole.fromId(tag.getString("order_role"));
         }
         if (tag.contains("devotion")) {
            record.devotion = tag.getInt("devotion");
         }
         if (tag.hasUUID("household_id")) {
            record.householdId = tag.getUUID("household_id");
         }
         if (tag.hasUUID("spouse_uuid")) {
            record.spouseUuid = tag.getUUID("spouse_uuid");
         }
         if (tag.hasUUID("mother_uuid")) {
            record.motherUuid = tag.getUUID("mother_uuid");
         }
         if (tag.hasUUID("father_uuid")) {
            record.fatherUuid = tag.getUUID("father_uuid");
         }
         if (tag.hasUUID("mentor_uuid")) {
            record.mentorUuid = tag.getUUID("mentor_uuid");
         }
         if (tag.contains("apprentices")) {
            ListTag apps = tag.getList("apprentices", 10);
            for (int i = 0; i < apps.size(); i++) {
               CompoundTag a = apps.getCompound(i);
               if (a.hasUUID("id")) {
                  record.apprenticeUuids.add(a.getUUID("id"));
               }
            }
         }
         if (tag.contains("craft_xp")) {
            CompoundTag xpTag = tag.getCompound("craft_xp");
            for (String key : xpTag.getAllKeys()) {
               record.craftXp.put(key, xpTag.getInt(key));
            }
         }
         if (tag.contains("memories")) {
            ListTag mem = tag.getList("memories", 8);
            for (int i = 0; i < mem.size(); i++) {
               record.memories.add(mem.getString(i));
            }
         }

         return record;
      } catch (Exception e) {
         LOGGER.error("Unable to load VillagerRecord from NBT: {}", e.getMessage());
         return null;
      }
   }
}
