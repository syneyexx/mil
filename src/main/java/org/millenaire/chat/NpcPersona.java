package org.millenaire.chat;

import java.util.List;

public record NpcPersona(
   String name,
   String familyName,
   String role,
   String nativeName,
   String gender,
   boolean child,
   String father,
   String mother,
   String partner,
   String cultureId,
   String cultureDisplayName,
   String villageName,
   String villageType,
   List<String> tags,
   String goalLabel,
   boolean underAttack,
   String timeOfDay,
   boolean raining,
   int reputation,
   String reputationLabel,
   String languageAside,
   boolean hiredByPlayer,
   String mood,
   String lastThought,
   float trust,
   float fear,
   float affection,
   String summary,
   List<String> factLines,
   List<String> quoteLines,
   List<String> turnLines,
   List<String> rumorLines,
   String playerLanguage,
   String playerSpeech
) {
}
