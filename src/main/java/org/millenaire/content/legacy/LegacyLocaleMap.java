package org.millenaire.content.legacy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class LegacyLocaleMap {
   public static final String NATIVE = "native";
   private static final Pattern MODERN_LOCALE = Pattern.compile("[a-z]{2,3}_[a-z]{2,4}");
   private static final Map<String, String> TABLE;

   private LegacyLocaleMap() {
   }

   public static Optional<String> toLocale(String folder) {
      if (folder == null) {
         return Optional.empty();
      }

      String f = folder.toLowerCase(Locale.ROOT);
      return isModernFolder(f) ? Optional.of(f) : Optional.ofNullable(TABLE.get(f));
   }

   public static boolean isModernFolder(String folder) {
      if (folder == null) {
         return false;
      }

      String f = folder.toLowerCase(Locale.ROOT);
      return "native".equals(f) || MODERN_LOCALE.matcher(f).matches();
   }

   public static boolean isLegacyCode(String folder) {
      if (folder == null) {
         return false;
      }

      String f = folder.toLowerCase(Locale.ROOT);
      return !isModernFolder(f) && TABLE.containsKey(f);
   }

   public static boolean isRegionalLegacyCode(String folder) {
      if (folder == null) {
         return false;
      }

      String f = folder.toLowerCase(Locale.ROOT);
      return !"native".equals(f) && isModernFolder(f) && TABLE.containsKey(f);
   }

   public static Optional<String> toQuestTextCode(String folder) {
      return toLocale(folder).map(locale -> {
         String f = folder.toLowerCase(Locale.ROOT);
         if (f.equals(locale)) {
            return (String)locale;
         }

         int sep = locale.indexOf(95);
         return sep > 0 ? locale.substring(0, sep) : locale;
      });
   }

   public static Map<String, String> legacyEntries() {
      return TABLE;
   }

   static {
      Map<String, String> t = new LinkedHashMap<>();
      t.put("en", "en_us");
      t.put("fr", "fr_fr");
      t.put("ar", "ar_sa");
      t.put("cs", "cs_cz");
      t.put("da", "da_dk");
      t.put("de", "de_de");
      t.put("dk", "da_dk");
      t.put("es", "es_es");
      t.put("et", "et_ee");
      t.put("hi", "hi_in");
      t.put("hu", "hu_hu");
      t.put("it", "it_it");
      t.put("ja", "ja_jp");
      t.put("ko", "ko_kr");
      t.put("nb", "nb_no");
      t.put("nl", "nl_nl");
      t.put("no", "nb_no");
      t.put("pl", "pl_pl");
      t.put("pt", "pt_pt");
      t.put("pt_br", "pt_br");
      t.put("ru", "ru_ru");
      t.put("sl", "sl_si");
      t.put("sv", "sv_se");
      t.put("th", "th_th");
      t.put("tr", "tr_tr");
      t.put("uk", "uk_ua");
      t.put("zh_cn", "zh_cn");
      t.put("zh_tw", "zh_tw");
      TABLE = Collections.unmodifiableMap(t);
   }
}
