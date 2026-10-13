package org.millenaire.monster.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.millenaire.monster.NativeMonster;

/** Native per-face UV renderer. Cubes are baked once at load, then animated by bone. */
public class NativeMonsterModel<T extends NativeMonster> extends EntityModel<T> {
   private static final float RAD = (float) (Math.PI / 180);
   private static final String[] FACE_NAMES = {"north", "south", "east", "west", "up", "down"};
   private static final int[][][] CORNERS = {
      {{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}},
      {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},
      {{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}},
      {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}},
      {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
      {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}}
   };
   private static final float[][] NORMALS = {{0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

   private final Map<String, Bone> bones = new LinkedHashMap<>();
   private final JsonObject clips;
   private final List<Bone> roots = new ArrayList<>();

   private record Quad(Vector3f[] positions, float[][] uv, Vector3f normal, boolean fullbright) {}

   private static final class Bone {
      final String name;
      final Vector3f pivot;
      final Vector3f rotation;
      final List<Quad> quads = new ArrayList<>();
      final List<Bone> children = new ArrayList<>();
      Vector3f localPivot;
      Vector3f animatedPosition = new Vector3f();
      Vector3f animatedRotation = new Vector3f();

      Bone(String name, Vector3f pivot, Vector3f rotation) {
         this.name = name;
         this.pivot = pivot;
         this.rotation = rotation;
      }
   }

   public NativeMonsterModel(String id) {
      super(RenderType::entityCutoutNoCull);
      ResourceLocation location = ResourceLocation.fromNamespaceAndPath("millenaire", "models/entity/monster/" + id + ".model.json");
      JsonObject asset;
      try (Reader reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(location).openAsReader()) {
         asset = JsonParser.parseReader(reader).getAsJsonObject();
      } catch (IOException exception) {
         throw new IllegalStateException("Cannot load monster model " + location, exception);
      }
      clips = asset.getAsJsonObject("clips");
      for (JsonElement entry : asset.getAsJsonArray("groups")) {
         JsonObject g = entry.getAsJsonObject();
         bones.put(g.get("name").getAsString(), new Bone(g.get("name").getAsString(), vec(g.getAsJsonArray("origin")), vec(g.getAsJsonArray("rotation"))));
      }
      for (JsonElement entry : asset.getAsJsonArray("groups")) {
         JsonObject g = entry.getAsJsonObject();
         Bone bone = bones.get(g.get("name").getAsString());
         Bone parent = g.get("parent").isJsonNull() ? null : bones.get(g.get("parent").getAsString());
         bone.localPivot = new Vector3f(bone.pivot).sub(parent == null ? new Vector3f() : parent.pivot).div(16);
         if (parent == null) {
            roots.add(bone);
         } else {
            parent.children.add(bone);
         }
      }
      JsonArray dimensions = asset.getAsJsonArray("texture_size");
      float width = dimensions.get(0).getAsFloat();
      float height = dimensions.get(1).getAsFloat();
      for (JsonElement entry : asset.getAsJsonArray("elements")) {
         JsonObject e = entry.getAsJsonObject();
         Bone bone = bones.get(e.get("_bone").getAsString());
         Vector3f low = vec(e.getAsJsonArray("from"));
         Vector3f high = vec(e.getAsJsonArray("to"));
         Vector3f pivot = vec(e.getAsJsonArray("origin"));
         Vector3f rotation = vec(e.getAsJsonArray("rotation"));
         Quaternionf quaternion = new Quaternionf().rotationZYX(rotation.z * RAD, rotation.y * RAD, rotation.x * RAD);
         boolean emissive = e.has("_emissive") && e.get("_emissive").getAsBoolean();
         for (int face = 0; face < 6; face++) {
            Vector3f[] vertices = new Vector3f[4];
            for (int vertex = 0; vertex < 4; vertex++) {
               int[] c = CORNERS[face][vertex];
               Vector3f v = new Vector3f(c[0] == 0 ? low.x : high.x, c[1] == 0 ? low.y : high.y, c[2] == 0 ? low.z : high.z);
               quaternion.transform(v.sub(pivot));
               vertices[vertex] = v.add(pivot).sub(bone.pivot).div(16);
            }
            JsonArray rectangle = e.getAsJsonObject("faces").getAsJsonObject(FACE_NAMES[face]).getAsJsonArray("uv");
            float u0 = rectangle.get(0).getAsFloat() / width;
            float v0 = rectangle.get(1).getAsFloat() / height;
            float u1 = rectangle.get(2).getAsFloat() / width;
            float v1 = rectangle.get(3).getAsFloat() / height;
            float[][] uv = face == 0
               ? new float[][] {{u1, v1}, {u0, v1}, {u0, v0}, {u1, v0}}
               : new float[][] {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
            Vector3f normal = quaternion.transform(new Vector3f(NORMALS[face][0], NORMALS[face][1], NORMALS[face][2]));
            bone.quads.add(new Quad(vertices, uv, normal, emissive));
         }
      }
   }

   private static Vector3f vec(JsonArray a) {
      return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
   }

   private static Vector3f sample(JsonArray frames, float time) {
      JsonArray first = frames.get(0).getAsJsonArray();
      if (time <= first.get(0).getAsFloat()) {
         return vec(first.get(1).getAsJsonArray());
      }
      for (int i = 1; i < frames.size(); i++) {
         JsonArray previous = frames.get(i - 1).getAsJsonArray();
         JsonArray next = frames.get(i).getAsJsonArray();
         float end = next.get(0).getAsFloat();
         if (time <= end) {
            float start = previous.get(0).getAsFloat();
            return vec(previous.get(1).getAsJsonArray()).lerp(vec(next.get(1).getAsJsonArray()), (time - start) / (end - start));
         }
      }
      return vec(frames.get(frames.size() - 1).getAsJsonArray().get(1).getAsJsonArray());
   }

   private void applyClip(String name, float time, float weight) {
      if (!clips.has(name) || weight <= 0) {
         return;
      }
      JsonObject clip = clips.getAsJsonObject(name);
      float length = clip.get("length").getAsFloat();
      time = clip.get("loop").getAsBoolean() ? time % length : Math.min(time, length);
      for (Map.Entry<String, JsonElement> entry : clip.getAsJsonObject("bones").entrySet()) {
         Bone bone = bones.get(entry.getKey());
         if (bone == null) {
            continue;
         }
         JsonObject channels = entry.getValue().getAsJsonObject();
         if (channels.has("position")) {
            bone.animatedPosition.add(sample(channels.getAsJsonArray("position"), time).mul(weight / 16));
         }
         if (channels.has("rotation")) {
            bone.animatedRotation.add(sample(channels.getAsJsonArray("rotation"), time).mul(weight));
         }
      }
   }

   @Override
   public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float headYaw, float headPitch) {
      for (Bone bone : bones.values()) {
         bone.animatedPosition.zero();
         bone.animatedRotation.zero();
      }
      float partialTick = ageInTicks - entity.tickCount;
      applyClip("idle", ageInTicks / 20, 1);
      applyClip("walk", limbSwing / 5, Math.min(1, limbSwingAmount));
      float attack = entity.attackTime(partialTick);
      if (attack >= 0) {
         applyClip("attack", attack, 1);
      }
      if (entity.hurtTime > 0) {
         applyClip("hurt", Math.max(0, (entity.hurtDuration - entity.hurtTime + partialTick) / 20), 1);
      }
      if (entity.isPlayingArrival()) {
         applyClip("arrival", ageInTicks / 20, 1);
      }
      if (entity.isDeadOrDying()) {
         applyClip("death", (entity.deathTime + partialTick) / 20, 1);
      }
      Bone head = bones.get("head");
      if (head != null) {
         head.animatedRotation.add(Math.max(-12, Math.min(12, headPitch)), Math.max(-18, Math.min(18, headYaw)), 0);
      }
   }

   private static void rotate(PoseStack pose, Vector3f r) {
      pose.mulPose(Axis.ZP.rotationDegrees(r.z));
      pose.mulPose(Axis.YP.rotationDegrees(r.y));
      pose.mulPose(Axis.XP.rotationDegrees(r.x));
   }

   @Override
   public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay, int color) {
      pose.pushPose();
      pose.translate(0, 1.5, 0);
      pose.scale(1, -1, 1);
      for (Bone root : roots) {
         renderBone(root, pose, vertices, light, overlay, color);
      }
      pose.popPose();
   }

   private void renderBone(Bone bone, PoseStack pose, VertexConsumer vertices, int light, int overlay, int color) {
      pose.pushPose();
      Vector3f p = new Vector3f(bone.localPivot).add(bone.animatedPosition);
      pose.translate(p.x, p.y, p.z);
      rotate(pose, bone.rotation);
      rotate(pose, bone.animatedRotation);
      PoseStack.Pose current = pose.last();
      for (Quad quad : bone.quads) {
         int packedLight = quad.fullbright ? 0xF000F0 : light;
         for (int i = 0; i < 4; i++) {
            Vector3f v = quad.positions[i];
            float[] uv = quad.uv[i];
            Vector3f n = quad.normal;
            vertices.addVertex(current, v.x, v.y, v.z)
               .setColor(color)
               .setUv(uv[0], uv[1])
               .setOverlay(overlay)
               .setLight(packedLight)
               .setNormal(current, n.x, n.y, n.z);
         }
      }
      for (Bone child : bone.children) {
         renderBone(child, pose, vertices, light, overlay, color);
      }
      pose.popPose();
   }
}
