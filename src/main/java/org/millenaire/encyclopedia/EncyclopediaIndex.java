package org.millenaire.encyclopedia;

import java.util.List;

public record EncyclopediaIndex(int schemaVersion, String modVersion, String generatedAt, List<IndexEntry> items) {
   public static final int SCHEMA_VERSION = 1;
}
