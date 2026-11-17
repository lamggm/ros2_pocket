package com.lamggm.rospocket;
import org.json.*;import static org.junit.Assert.*;
/** Downgrade only default-valued NEW fields when deliberately constructing a legacy fixture.
 * Never used by the production parser; strict unknown-field assertions remain unchanged. */
final class LegacyCodecFixtures {
 static JSONObject layout(JSONObject j)throws JSONException{if(j.getInt("schema")<5){JSONArray cards=j.getJSONArray("cards");for(int i=0;i<cards.length();i++){JSONObject d=cards.getJSONObject(i).optJSONObject("display");if(d!=null&&d.has("decimals")){assertEquals("Cannot discard configured precision in a legacy fixture",-1,d.getInt("decimals"));d.remove("decimals");}}}return j;}
 static void profile(JSONObject p)throws JSONException{if(p.has("robot")){assertTrue("Cannot discard an actual robot binding",p.isNull("robot"));p.remove("robot");}JSONArray plots=p.getJSONArray("plots");for(int i=0;i<plots.length();i++){JSONObject s=plots.getJSONObject(i);if(s.has("unit")){assertEquals("Cannot discard a declared unit","",s.getString("unit"));s.remove("unit");}}}
}
