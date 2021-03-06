package exerelin.campaign.customstart;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import exerelin.ExerelinConstants;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CustomStartDefs {
	public static final String CONFIG_FILE = "data/config/exerelin/customStarts.json";
	protected static final List<CustomStartDef> defs = new ArrayList<>();
	protected static final Map<String, CustomStartDef> defsByID = new HashMap<>();
	protected static final Map<String, Color> difficultyColors = new HashMap<>(); 
	
	static {
		try {
            loadSettings();
        } catch (IOException | JSONException | NullPointerException ex) {
            throw new RuntimeException(ex);
        }
	}
	
	protected static void loadSettings() throws IOException, JSONException {
		JSONObject baseJson = Global.getSettings().getMergedJSONForMod(CONFIG_FILE, ExerelinConstants.MOD_ID);
		
		// load colors
		JSONObject configJson = baseJson.getJSONObject("config");
		JSONObject colorsJson = configJson.getJSONObject("colors");
		Iterator<String> keys = colorsJson.sortedKeys();
		while (keys.hasNext()) {
			String difficulty = keys.next();
			JSONArray colorJson = colorsJson.getJSONArray(difficulty);
			Color color = new Color(colorJson.getInt(0), colorJson.getInt(1), colorJson.getInt(2));
			difficultyColors.put(difficulty, color);
		}
		
		// load custom start defs
		JSONArray scenariosJson = baseJson.getJSONArray("starts");
		for (int i = 0; i < scenariosJson.length(); i++)
		{
			JSONObject defJson = scenariosJson.getJSONObject(i);
			String id = defJson.getString("id");
			String name = defJson.getString("name");
			String desc = defJson.getString("desc");
			String className = defJson.getString("className");
			
			CustomStartDef def = new CustomStartDef(id, name, desc, className);
			def.requiredModId = defJson.optString("requiredModId", null);
			def.difficulty = defJson.optString("difficulty", StringHelper.getString("unknown"));
			def.factionId = defJson.optString("factionId", Factions.PLAYER);
			def.randomSector = defJson.optInt("randomSector", 0);
			//def.configStartingResources = defJson.optBoolean("configStartingResources", true);
			
			defs.add(def);
			defsByID.put(id, def);
		}
	}
	
	public static CustomStartDef getStartDef(String id) {
		return defsByID.get(id);
	}
	
	public static List<CustomStartDef> getStartDefs() {
		return new ArrayList<>(defs);
	}
	
	public static Color getDifficultyColor(String difficulty) {
		difficulty = difficulty.toLowerCase(Locale.ROOT);
		if (!difficultyColors.containsKey(difficulty))
			return Misc.getHighlightColor();
		return difficultyColors.get(difficulty);
	}
	
	public static void loadCustomStart(String id, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		CustomStartDef def = defsByID.get(id);
		try {
			ClassLoader loader = Global.getSettings().getScriptClassLoader();
			Class<?> clazz = loader.loadClass(def.className);
			CustomStart start = (CustomStart)clazz.newInstance();
			start.execute(dialog, memoryMap);
		} catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
			//Global.getLogger(StartScenarioManager.class).error("Failed to load scenario " + id, ex);
			throw new RuntimeException("Failed to load custom start " + id, ex);
		}
	}
	
	public static class CustomStartDef {
		public final String id;
		public String name;
		public String desc;
		public String difficulty;
		public String factionId = Factions.PLAYER;
		public String className;
		public String requiredModId;
		public int randomSector;
		//public boolean configStartingResources = true;
		
		public CustomStartDef(String id, String name, String desc, String className) {
			this.id = id;
			this.name = name;
			this.desc = desc;
			this.className = className;
		}
	}
}
