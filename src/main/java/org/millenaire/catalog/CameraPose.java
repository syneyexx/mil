package org.millenaire.catalog;

import net.minecraft.world.phys.Vec3;

public record CameraPose(Vec3 pos, float yRot, float xRot, float fov) {
}
