package org.millenaire.building;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;

public final class ExportTransaction implements AutoCloseable {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final String STAGING_DIR = ".staging";
   public static final String TRASH_DIR = ".trash";
   private static final String BACKUP_DIR = ".backup";
   private static final Set<Path> IN_FLIGHT = Collections.synchronizedSet(new HashSet<>());
   private final Path stagingDir;
   private final Path targetDir;
   private final Map<String, Path> staged = new LinkedHashMap<>();
   @Nullable
   private String markerName;
   private boolean committed;
   private boolean closed;

   private ExportTransaction(Path stagingDir, Path targetDir) {
      this.stagingDir = stagingDir;
      this.targetDir = targetDir;
   }

   public static ExportTransaction begin(Path exportedRoot, String culturePath, String buildingId, Path targetDir) {
      Path stagingDir = exportedRoot.resolve(".staging").resolve(culturePath).resolve(buildingId);

      try {
         deleteRecursively(stagingDir);
         Files.createDirectories(stagingDir);
      } catch (IOException e) {
         throw new UncheckedIOException("Cannot open export staging at " + stagingDir, e);
      }

      IN_FLIGHT.add(stagingDir.toAbsolutePath().normalize());
      return new ExportTransaction(stagingDir, targetDir);
   }

   public Path targetDir() {
      return this.targetDir;
   }

   public boolean isStaged(String fileName) {
      return this.staged.containsKey(fileName);
   }

   public boolean willExist(String fileName) {
      return this.isStaged(fileName) || Files.isRegularFile(this.targetDir.resolve(fileName));
   }

   public void stageNbt(String fileName, CompoundTag nbt) throws IOException {
      Path path = this.stagePath(fileName);
      NbtIo.writeCompressed(nbt, path);
      fsync(path);
      this.staged.put(fileName, path);
   }

   public void stageCopy(String fileName, Path source) throws IOException {
      Path path = this.stagePath(fileName);
      Files.copy(source, path, StandardCopyOption.REPLACE_EXISTING);
      fsync(path);
      this.staged.put(fileName, path);
   }

   public void stageBytes(String fileName, byte[] body) throws IOException {
      Path path = this.stagePath(fileName);
      Files.write(path, body);
      fsync(path);
      this.staged.put(fileName, path);
   }

   public void stageMarker(String fileName, String body) throws IOException {
      Path path = this.stagePath(fileName);
      Files.write(path, body.getBytes(StandardCharsets.UTF_8));
      fsync(path);
      this.staged.put(fileName, path);
      this.markerName = fileName;
   }

   public void commit() throws IOException {
      if (!this.committed) {
         Files.createDirectories(this.targetDir);
         List<String> ordered = new ArrayList<>(this.staged.keySet());
         ordered.sort(Comparator.naturalOrder());
         if (this.markerName != null) {
            ordered.remove(this.markerName);
            ordered.add(this.markerName);
         }

         Path backupDir = this.stagingDir.resolve(".backup");
         List<String> touched = new ArrayList<>();

         try {
            for (String name : ordered) {
               Path target = this.targetDir.resolve(name);
               if (Files.exists(target)) {
                  Files.createDirectories(backupDir);
                  move(target, backupDir.resolve(name));
               }

               touched.add(name);
               move(this.staged.get(name), target);
            }
         } catch (IOException | RuntimeException e) {
            this.rollback(touched, backupDir);
            throw e;
         }

         fsyncDir(this.targetDir);
         this.committed = true;
         this.cleanupStaging();
      }
   }

   private void rollback(List<String> touched, Path backupDir) {
      for (String name : touched) {
         Path target = this.targetDir.resolve(name);
         Path backup = backupDir.resolve(name);

         try {
            Files.deleteIfExists(target);
            if (Files.exists(backup)) {
               move(backup, target);
            }
         } catch (IOException e) {
            LOGGER.error("Could not roll back {} in {} — recover it from {}", new Object[]{name, this.targetDir, backup, e});
         }
      }
   }

   public void abort() {
      if (!this.committed) {
         this.cleanupStaging();
      }
   }

   @Override
   public void close() {
      if (!this.closed) {
         this.closed = true;
         if (!this.committed) {
            this.abort();
         }
      }
   }

   private Path stagePath(String fileName) {
      if (fileName != null && !fileName.isEmpty() && fileName.indexOf(47) < 0 && fileName.indexOf(92) < 0 && !fileName.contains("..")) {
         return this.stagingDir.resolve(fileName);
      } else {
         throw new IllegalArgumentException("unsafe staged file name: " + fileName);
      }
   }

   private void cleanupStaging() {
      IN_FLIGHT.remove(this.stagingDir.toAbsolutePath().normalize());

      try {
         deleteRecursively(this.stagingDir);
      } catch (IOException e) {
         LOGGER.warn("Could not clean export staging {}: {}", this.stagingDir, e.getMessage());
      }
   }

   public static void reclaimStaging(Path exportedRoot) {
      Path stagingRoot = exportedRoot.resolve(".staging");
      if (Files.isDirectory(stagingRoot)) {
         try (Stream<Path> cultures = Files.list(stagingRoot)) {
            for (Path culture : cultures.filter(x$0 -> Files.isDirectory(x$0)).toList()) {
               try (Stream<Path> buildings = Files.list(culture)) {
                  for (Path dir : buildings.filter(x$0 -> Files.isDirectory(x$0)).toList()) {
                     if (!IN_FLIGHT.contains(dir.toAbsolutePath().normalize())) {
                        LOGGER.info("Reclaiming abandoned export staging {}", dir);
                        deleteRecursively(dir);
                     }
                  }
               }
            }
         } catch (IOException e) {
            LOGGER.warn("Could not reclaim export staging under {}: {}", stagingRoot, e.getMessage());
         }
      }
   }

   private static void move(Path source, Path target) throws IOException {
      try {
         Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
         Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   private static void fsync(Path path) {
      try (FileChannel ch = FileChannel.open(path, StandardOpenOption.WRITE)) {
         ch.force(true);
      } catch (IOException e) {
         LOGGER.debug("fsync failed on {}: {}", path, e.getMessage());
      }
   }

   private static void fsyncDir(Path dir) {
      try (FileChannel ch = FileChannel.open(dir, StandardOpenOption.READ)) {
         ch.force(true);
      } catch (IOException | UnsupportedOperationException e) {
         LOGGER.debug("fsync failed on directory {}: {}", dir, e.getMessage());
      }
   }

   static void deleteRecursively(Path root) throws IOException {
      if (Files.exists(root)) {
         try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
               Files.deleteIfExists(p);
            }
         }
      }
   }
}
