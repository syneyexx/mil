package org.millenaire.worldgen.highway;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import org.millenaire.worldgen.materialize.RoadPlacementGuard;

public final class HighwayRoute {
   public enum Status {
      PLANNED,
      PARTIAL,
      WAITING_CHUNKS,
      BLOCKED,
      CONNECTED,
      FAILED;

      static Status fromId(String id) {
         try {
            return Status.valueOf(id);
         } catch (Exception e) {
            return PLANNED;
         }
      }
   }

   private final UUID routeId;
   private final String fromNodeKey;
   private final String toNodeKey;
   private final List<BlockPos> waypoints;
   private final Set<Long> completedCells = new HashSet<>();
   private Status status = Status.PLANNED;
   private String blockReason = "";
   private int cursor;
   private int bridgeSupportCursor;
   private int bridgeDeckY = Integer.MIN_VALUE;
   private int tunnelCursor;
   private int gradeY = Integer.MIN_VALUE;
   private int lastPlacedX = Integer.MIN_VALUE;
   private int lastPlacedY = Integer.MIN_VALUE;
   private int lastPlacedZ = Integer.MIN_VALUE;

   public HighwayRoute(UUID routeId, String fromNodeKey, String toNodeKey, List<BlockPos> waypoints) {
      this.routeId = routeId;
      this.fromNodeKey = fromNodeKey;
      this.toNodeKey = toNodeKey;
      this.waypoints = new ArrayList<>(waypoints);
   }

   public UUID routeId() {
      return this.routeId;
   }

   public String fromNodeKey() {
      return this.fromNodeKey;
   }

   public String toNodeKey() {
      return this.toNodeKey;
   }

   public List<BlockPos> waypoints() {
      return this.waypoints;
   }

   public Set<Long> completedCells() {
      return this.completedCells;
   }

   public Status status() {
      return this.status;
   }

   public void setStatus(Status status) {
      this.status = status == null ? Status.PLANNED : status;
   }

   public String blockReason() {
      return this.blockReason;
   }

   public void setBlockReason(String reason) {
      this.blockReason = reason == null ? "" : reason;
   }

   public int cursor() {
      return this.cursor;
   }

   public void setCursor(int cursor) {
      this.cursor = Math.max(0, cursor);
   }

   public int bridgeSupportCursor() {
      return this.bridgeSupportCursor;
   }

   public void setBridgeSupportCursor(int cursor) {
      this.bridgeSupportCursor = Math.max(0, cursor);
   }

   public int bridgeDeckY() {
      return this.bridgeDeckY;
   }

   public void setBridgeDeckY(int y) {
      this.bridgeDeckY = y;
   }

   public int tunnelCursor() {
      return this.tunnelCursor;
   }

   public void setTunnelCursor(int cursor) {
      this.tunnelCursor = Math.max(0, cursor);
   }

   public int gradeY() {
      return this.gradeY;
   }

   public void setGradeY(int y) {
      this.gradeY = y;
   }

   @Nullable
   public BlockPos lastPlacedSurface() {
      if (this.lastPlacedX == Integer.MIN_VALUE) {
         return null;
      }
      return new BlockPos(this.lastPlacedX, this.lastPlacedY, this.lastPlacedZ);
   }

   public void setLastPlacedSurface(@Nullable BlockPos pos) {
      if (pos == null) {
         this.lastPlacedX = Integer.MIN_VALUE;
         this.lastPlacedY = Integer.MIN_VALUE;
         this.lastPlacedZ = Integer.MIN_VALUE;
         return;
      }
      this.lastPlacedX = pos.getX();
      this.lastPlacedY = pos.getY();
      this.lastPlacedZ = pos.getZ();
   }

   public boolean connects(String a, String b) {
      return (this.fromNodeKey.equals(a) && this.toNodeKey.equals(b))
         || (this.fromNodeKey.equals(b) && this.toNodeKey.equals(a));
   }

   public CompoundTag save() {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("id", this.routeId);
      tag.putString("from", this.fromNodeKey);
      tag.putString("to", this.toNodeKey);
      tag.putString("status", this.status.name());
      tag.putString("block", this.blockReason);
      tag.putInt("cursor", this.cursor);
      tag.putInt("bridgeSupport", this.bridgeSupportCursor);
      tag.putInt("bridgeDeckY", this.bridgeDeckY);
      tag.putInt("tunnelCursor", this.tunnelCursor);
      tag.putInt("gradeY", this.gradeY);
      tag.putInt("lastX", this.lastPlacedX);
      tag.putInt("lastY", this.lastPlacedY);
      tag.putInt("lastZ", this.lastPlacedZ);
      ListTag pts = new ListTag();
      for (BlockPos p : this.waypoints) {
         CompoundTag pt = new CompoundTag();
         pt.putInt("x", p.getX());
         pt.putInt("y", p.getY());
         pt.putInt("z", p.getZ());
         pts.add(pt);
      }
      tag.put("waypoints", pts);
      ListTag cells = new ListTag();
      for (long cell : this.completedCells) {
         cells.add(LongTag.valueOf(cell));
      }
      tag.put("doneCells", cells);
      return tag;
   }

   public static HighwayRoute load(CompoundTag tag) {
      UUID id = tag.getUUID("id");
      String from = tag.getString("from");
      String to = tag.getString("to");
      ListTag pts = tag.getList("waypoints", Tag.TAG_COMPOUND);
      List<BlockPos> waypoints = new ArrayList<>();
      for (int i = 0; i < pts.size(); i++) {
         CompoundTag pt = pts.getCompound(i);
         waypoints.add(new BlockPos(pt.getInt("x"), pt.getInt("y"), pt.getInt("z")));
      }
      HighwayRoute route = new HighwayRoute(id, from, to, waypoints);
      route.status = Status.fromId(tag.getString("status"));
      route.blockReason = tag.getString("block");
      route.cursor = tag.getInt("cursor");
      route.bridgeSupportCursor = tag.getInt("bridgeSupport");
      route.bridgeDeckY = tag.contains("bridgeDeckY") ? tag.getInt("bridgeDeckY") : Integer.MIN_VALUE;
      route.tunnelCursor = tag.getInt("tunnelCursor");
      route.gradeY = tag.contains("gradeY") ? tag.getInt("gradeY") : Integer.MIN_VALUE;
      route.lastPlacedX = tag.contains("lastX") ? tag.getInt("lastX") : Integer.MIN_VALUE;
      route.lastPlacedY = tag.contains("lastY") ? tag.getInt("lastY") : Integer.MIN_VALUE;
      route.lastPlacedZ = tag.contains("lastZ") ? tag.getInt("lastZ") : Integer.MIN_VALUE;
      ListTag cells = tag.getList("doneCells", Tag.TAG_LONG);
      for (int i = 0; i < cells.size(); i++) {
         route.completedCells.add(((net.minecraft.nbt.NumericTag)cells.get(i)).getAsLong());
      }
      return route;
   }

   public static long packCell(int x, int z) {
      return RoadPlacementGuard.packCell(x, z);
   }
}
