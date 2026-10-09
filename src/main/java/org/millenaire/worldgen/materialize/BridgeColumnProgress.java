package org.millenaire.worldgen.materialize;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Resumable bridge column state shared by local chunk progress and regional route progress.
 */
public interface BridgeColumnProgress {
   int bridgeSupportCursor(UUID key);

   void setBridgeSupportCursor(UUID key, int cursor);

   void clearBridgeSupportCursor(UUID key);

   int bridgeDeckHeight(UUID key, int fallback);

   void setBridgeDeckHeight(UUID key, int height);

   default boolean hasBridgeDeckHeight(UUID key) {
      return bridgeDeckHeight(key, Integer.MIN_VALUE) != Integer.MIN_VALUE;
   }

   /** Single-column adapter for regional routes that store one active bridge cursor. */
   final class SingleColumn implements BridgeColumnProgress {
      private int supportCursor;
      private int deckY = Integer.MIN_VALUE;
      private boolean supportComplete;
      private boolean deckComplete;
      private boolean passageComplete;

      public SingleColumn(int supportCursor, int deckY) {
         this.supportCursor = Math.max(0, supportCursor);
         this.deckY = deckY;
      }

      public int supportCursorValue() {
         return this.supportCursor;
      }

      public int deckYValue() {
         return this.deckY;
      }

      public boolean supportComplete() {
         return this.supportComplete;
      }

      public void setSupportComplete(boolean v) {
         this.supportComplete = v;
      }

      public boolean deckComplete() {
         return this.deckComplete;
      }

      public void setDeckComplete(boolean v) {
         this.deckComplete = v;
      }

      public boolean passageComplete() {
         return this.passageComplete;
      }

      public void setPassageComplete(boolean v) {
         this.passageComplete = v;
      }

      @Override
      public int bridgeSupportCursor(UUID key) {
         return this.supportCursor;
      }

      @Override
      public void setBridgeSupportCursor(UUID key, int cursor) {
         this.supportCursor = Math.max(0, cursor);
      }

      @Override
      public void clearBridgeSupportCursor(UUID key) {
         this.supportCursor = 0;
         this.deckY = Integer.MIN_VALUE;
         this.supportComplete = false;
         this.deckComplete = false;
         this.passageComplete = false;
      }

      @Override
      public int bridgeDeckHeight(UUID key, int fallback) {
         return this.deckY == Integer.MIN_VALUE ? fallback : this.deckY;
      }

      @Override
      public void setBridgeDeckHeight(UUID key, int height) {
         this.deckY = height;
      }

      @Override
      public boolean hasBridgeDeckHeight(UUID key) {
         return this.deckY != Integer.MIN_VALUE;
      }
   }

   static BridgeColumnProgress fromChunk(@Nullable ChunkMaterializationProgress progress) {
      return progress;
   }
}
