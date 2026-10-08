package org.millenaire.chat;

import java.util.UUID;
import javax.annotation.Nullable;

public record NpcFact(long id, String text, long gameTime, int importance, @Nullable UUID aboutPlayer) {
}
