package org.millenaire.monster.pack;

public enum MobKind {
   HORSE_WIZARD("horse_wizard", 32, 5, 0.23, 2, 0, 8, 0.8f, 2.35f, 0x352044, 0xB644EE),
   PEGASUS("pegasus", 36, 6, 0.30, 2, 0, 9, 0.9f, 1.8f, 0x43382E, 0xE49E29),
   ZUZU("zuzu", 48, 8, 0.20, 4, 0.25, 12, 0.9f, 2.35f, 0x494641, 0xDDDED7),
   BIRDMAN("birdman", 28, 6, 0.29, 1, 0, 7, 0.75f, 1.95f, 0x252C40, 0xD99E26),
   CTHULU("cthulu", 50, 9, 0.21, 3, 0.20, 12, 0.95f, 2.15f, 0x982626, 0x66D527),
   JUNRA("junra", 32, 5, 0.24, 2, 0, 7, 0.8f, 2.15f, 0x83532F, 0x4FAEBD),
   NEBULA("nebula", 40, 7, 0.28, 3, 0.10, 9, 0.8f, 2.15f, 0x21202D, 0xD3348E),
   SDA("sda", 44, 7, 0.22, 4, 0.15, 10, 0.9f, 2.15f, 0x737242, 0xE1D4A1),
   ZAVA("zava", 30, 5, 0.29, 1, 0, 7, 0.7f, 2.0f, 0x2D5BA8, 0x8F73BE),
   DISCD("discd", 42, 7, 0.23, 5, 0.15, 10, 0.9f, 2.1f, 0xABB2BE, 0xDEAD27);

   public final String id;
   public final double hp;
   public final double attack;
   public final double speed;
   public final double armor;
   public final double knockback;
   public final int xp;
   public final int eggBase;
   public final int eggAccent;
   public final float width;
   public final float height;

   MobKind(
      String id,
      double hp,
      double attack,
      double speed,
      double armor,
      double knockback,
      int xp,
      float width,
      float height,
      int eggBase,
      int eggAccent
   ) {
      this.id = id;
      this.hp = hp;
      this.attack = attack;
      this.speed = speed;
      this.armor = armor;
      this.knockback = knockback;
      this.xp = xp;
      this.width = width;
      this.height = height;
      this.eggBase = eggBase;
      this.eggAccent = eggAccent;
   }
}
