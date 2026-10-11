package org.millenaire.culture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class CulturalTemperamentParseTest {
   @Test
   void nullJsonYieldsDefault() {
      CulturalTemperament t = CulturalTemperament.parse(null);
      assertEquals(CulturalTemperament.DEFAULT, t);
      assertEquals("balanced", t.primaryLabel());
   }

   @Test
   void parsesAxesAndClamps() {
      JsonObject json = new JsonObject();
      json.addProperty("mercantile", 85);
      json.addProperty("martial", 10);
      json.addProperty("isolation", 200);
      json.addProperty("expansive", -5);
      json.addProperty("honor", 55);
      json.addProperty("hospitality", 85);
      json.addProperty("grudge", 35);
      json.addProperty("festival_affinity", 75);

      CulturalTemperament t = CulturalTemperament.parse(json);
      assertEquals(85, t.mercantile());
      assertEquals(10, t.martial());
      assertEquals(100, t.isolation());
      assertEquals(0, t.expansive());
      assertEquals("mercantile", t.primaryLabel());
      assertTrue(t.hospitalityWeight() > 1.0);
      assertTrue(t.tradeScoreBias() < 0);
   }

   @Test
   void martialPrimaryWhenDominant() {
      JsonObject json = new JsonObject();
      json.addProperty("martial", 90);
      json.addProperty("honor", 20);
      CulturalTemperament t = CulturalTemperament.parse(json);
      assertEquals("martial", t.primaryLabel());
   }
}
