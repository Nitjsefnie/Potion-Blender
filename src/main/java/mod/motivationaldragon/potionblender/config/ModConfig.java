package mod.motivationaldragon.potionblender.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import mod.motivationaldragon.potionblender.PotionBlender;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {

    private ModConfig(){}

    private static final String CONFIG_FILE_NAME = "potion_blender_config.json";
    private static final Path CONFIG_PATH = Path.of(PotionBlender.MODID, CONFIG_FILE_NAME);
    private static final Gson JSON_PARSER = new GsonBuilder().setPrettyPrinting().create();

    private static ConfigInstance config;

    private static boolean isReady = false;


    static {
        readConfig();
    }

    public static ConfigInstance getConfig(){
        return config;
    }

    private static void readConfig(){

            if(!isReady){initConfig();}

        try {
            String jsonString = Files.readString(CONFIG_PATH);
            ModConfig.config = deserializeConfig(jsonString);
            PotionBlender.LOGGER.info("Loaded config");
        } catch (IOException e) {
            PotionBlender.LOGGER.error("Could not read config file");
            e.printStackTrace();
        }

        PotionBlender.LOGGER.warn("Unable to read config, using a default one as fallback");
        ModConfig.config = new  ConfigInstance();
    }

    public static void initConfig() {

        if(isReady){return;}
        isReady = true;

        Path configPath = Path.of(PotionBlender.MODID, CONFIG_FILE_NAME);
        if(!Files.exists(configPath)){
            try {
                Path path = Path.of(PotionBlender.MODID);
                PotionBlender.LOGGER.info("No config file found, creating a new one at: "+ path + "...");
                Files.createDirectories(path);


                ConfigInstance configInstance = new ConfigInstance();

                String jsonString = serializeConfig(configInstance);
                Files.createFile(configPath);
                Files.writeString(configPath, jsonString);

            }catch (IOException e){
                PotionBlender.LOGGER.error("Could not access config file");
                e.printStackTrace();
            }
        }
    }


    public static ConfigInstance deserializeConfig(String configAsJson){
        try {
            return JSON_PARSER.fromJson(configAsJson, ConfigInstance.class);
        }catch (JsonSyntaxException e){
            PotionBlender.LOGGER.error("Could not parse config JSON. Make sure syntax is correct");
            e.printStackTrace();
        }
        PotionBlender.LOGGER.warn("Unable to deserialize config, using a default one as fallback");
        return new ConfigInstance();
    }

    public static String serializeConfig(ConfigInstance config){
        return JSON_PARSER.toJson(config);
    }

}
