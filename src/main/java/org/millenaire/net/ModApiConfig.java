package org.millenaire.net;

import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import org.millenaire.config.MillenaireServerConfig;
import org.slf4j.Logger;

public final class ModApiConfig {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final String SECRET;
   private static final String BAKED_BASE;
   private static final String FALLBACK_BASE;

   private ModApiConfig() {
   }

   public static String secret() {
      return SECRET;
   }

   public static boolean hasSecret() {
      return !SECRET.isBlank();
   }

   public static List<String> baseUrls() {
      return resolveBases((String)MillenaireServerConfig.SERVER.apiBaseUrlOverride.get(), BAKED_BASE, FALLBACK_BASE);
   }

   static List<String> resolveBases(String override, String primary, String fallback) {
      if (override != null && !override.isBlank()) {
         return List.of(normalizeBase(override.strip()));
      }

      String p = normalizeBase(primary);
      String f = normalizeBase(fallback);
      return p.equals(f) ? List.of(p) : List.of(p, f);
   }

   static String normalizeBase(String base) {
      return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
   }

   static {
      Properties props = new Properties();

      try (InputStream in = ModApiConfig.class.getResourceAsStream("/millenaire-build.properties")) {
         if (in != null) {
            props.load(in);
         } else {
            LOGGER.debug("millenaire-build.properties not found — mod API disabled");
         }
      } catch (Exception e) {
         LOGGER.debug("Could not read millenaire-build.properties: {}", e.getMessage());
      }

      SECRET = props.getProperty("mod.hmac.secret", "");
      BAKED_BASE = normalizeBase(props.getProperty("mod.api.base", "https://millenaire.org"));
      FALLBACK_BASE = normalizeBase(props.getProperty("mod.api.base.fallback", "https://new.millenaire.org"));
   }
}
