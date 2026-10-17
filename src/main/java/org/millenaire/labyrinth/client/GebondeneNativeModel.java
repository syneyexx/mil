package org.millenaire.labyrinth.client;

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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.millenaire.labyrinth.LabyrinthKeys;
import org.millenaire.labyrinth.entity.GebondeneEntity;

/**
 * Blockbench / NativeMonster-style JSON model for one Gebondene kind.
 * Loads {@code models/entity/gebondene/<id>.model.json}.
 */
@OnlyIn(Dist.CLIENT)
public class GebondeneNativeModel extends EntityModel<GebondeneEntity> {
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
   private final String kindId;

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

   public GebondeneNativeModel(String kindId) {
      super(RenderType::entityCutoutNoCull);
      this.kindId = kindId;
      ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
         LabyrinthKeys.MODID,
         "models/entity/gebondene/" + kindId + ".model.json"
      );
      JsonObject asset;
      try (Reader reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(location).openAsReader()) {
         asset = JsonParser.parseReader(reader).getAsJsonObject();
      } catch (IOException exception) {
         throw new IllegalStateException("Cannot load Gebondene model " + location, exception);
      }
      clips = asset.has("clips") && asset.get("clips").isJsonObject()
         ? asset.getAsJsonObject("clips")
         : new JsonObject();
      for (JsonElement entry : asset.getAsJsonArray("groups")) {
         JsonObject g = entry.getAsJsonObject();
         bones.put(
            g.get("name").getAsString(),
            new Bone(g.get("name").getAsString(), vec(g.getAsJsonArray("origin")), vec(g.getAsJsonArray("rotation")))
         );
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
         if (bone == null) {
            continue;
         }
         Vector3f low = vec(e.getAsJsonArray("from"));
         Vector3f high = vec(e.getAsJsonArray("to"));
         Vector3f pivot = vec(e.getAsJsonArray("origin"));
         Vector3f rotation = rotationOf(e);
         Quaternionf quaternion = new Quaternionf().rotationZYX(rotation.z * RAD, rotation.y * RAD, rotation.x * RAD);
         boolean emissive = e.has("_emissive") && e.get("_emissive").getAsBoolean();
         JsonObject faces = e.getAsJsonObject("faces");
         for (int face = 0; face < 6; face++) {
            Vector3f[] vertices = new Vector3f[4];
            for (int vertex = 0; vertex < 4; vertex++) {
               int[] c = CORNERS[face][vertex];
               Vector3f v = new Vector3f(
                  c[0] == 0 ? low.x : high.x,
                  c[1] == 0 ? low.y : high.y,
                  c[2] == 0 ? low.z : high.z
               );
               quaternion.transform(v.sub(pivot));
               vertices[vertex] = v.add(pivot).sub(bone.pivot).div(16);
            }
            JsonObject faceObj = faces.getAsJsonObject(FACE_NAMES[face]);
            JsonArray rectangle = faceObj.getAsJsonArray("uv");
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

   public String kindId() {
      return kindId;
   }

   private static Vector3f rotationOf(JsonObject e) {
      JsonElement rot = e.get("rotation");
      if (rot == null || rot.isJsonNull()) {
         return new Vector3f();
      }
      if (rot.isJsonArray()) {
         return vec(rot.getAsJsonArray());
      }
      if (rot.isJsonObject()) {
         JsonObject o = rot.getAsJsonObject();
         return new Vector3f(
            o.has("x") ? o.get("x").getAsFloat() : 0,
            o.has("y") ? o.get("y").getAsFloat() : 0,
            o.has("z") ? o.get("z").getAsFloat() : 0
         );
      }
      return new Vector3f();
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
   public void setupAnim(GebondeneEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float headYaw, float headPitch) {
      for (Bone bone : bones.values()) {
         bone.animatedPosition.zero();
         bone.animatedRotation.zero();
      }
      applyClip("idle", ageInTicks / 20, 1);
      applyClip("walk", limbSwing / 5, Math.min(1, limbSwingAmount));
      if (entity.hurtTime > 0) {
         applyClip("hurt", Math.max(0, (entity.hurtDuration - entity.hurtTime) / 20.0F), 1);
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
