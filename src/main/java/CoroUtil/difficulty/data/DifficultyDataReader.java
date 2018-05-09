package CoroUtil.difficulty.data;

import CoroUtil.config.ConfigCoroUtil;
import CoroUtil.difficulty.data.cmods.*;
import CoroUtil.difficulty.data.conditions.*;
import CoroUtil.forge.CULog;
import CoroUtil.forge.CoroUtil;
import CoroUtil.util.UtilClasspath;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.*;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import sun.misc.BASE64Encoder;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Corosus on 2/1/2017.
 *
 */
public class DifficultyDataReader {

    //using Class.class because we dont need a return type
    private static final Gson GSONBuffInventory = (new GsonBuilder()).registerTypeAdapter(DifficultyData.class, new DeserializerAllJson()).create();
    //private static final Gson GSON_INSTANCE = (new GsonBuilder()).registerTypeAdapter(RandomValueRange.class, new RandomValueRange.Serializer()).registerTypeAdapter(LootPool.class, new LootPool.Serializer()).registerTypeAdapter(LootTable.class, new LootTable.Serializer()).registerTypeHierarchyAdapter(LootEntry.class, new LootEntry.Serializer()).registerTypeHierarchyAdapter(LootFunction.class, new LootFunctionManager.Serializer()).registerTypeHierarchyAdapter(LootCondition.class, new LootConditionManager.Serializer()).registerTypeHierarchyAdapter(LootContext.EntityTarget.class, new LootContext.EntityTarget.Serializer()).create();

    private static DifficultyData data;

    public static String lootTablesFolder = "loot_tables";

    public static HashMap<String, Class> lookupJsonNameToCmodDeserializer = new HashMap<>();
    public static HashMap<String, Class> lookupJsonNameToConditionDeserializer = new HashMap<>();

    //modes used for validating
    private static boolean debugValidate = false;
    private static boolean debugFlattenCmodsAndConditions = false;

    public static String pathData = "./config/CoroUtil/data/";
    public static File dataFolder = new File(pathData);
    public static File dataHashes = new File(pathData + "filehashes.txt");

    public static boolean debugValidate() {
        return debugValidate;
    }

    public static void setDebugValidate(boolean debugValidate) {
        DifficultyDataReader.debugValidate = debugValidate;
    }

    public static boolean debugFlattenCmodsAndConditions() {
        return debugFlattenCmodsAndConditions;
    }

    public static void setDebugFlattenCmodsAndConditions(boolean debugFlattenCmodsAndConditions) {
        DifficultyDataReader.debugFlattenCmodsAndConditions = debugFlattenCmodsAndConditions;
    }

    public static void init() {
        data = new DifficultyData();

        lookupJsonNameToCmodDeserializer.clear();
        lookupJsonNameToCmodDeserializer.put("inventory", CmodInventory.class);
        lookupJsonNameToCmodDeserializer.put("mob_drops", CmodMobDrops.class);
        lookupJsonNameToCmodDeserializer.put("attribute_health", CmodAttributeHealth.class);
        lookupJsonNameToCmodDeserializer.put("attribute_speed", CmodAttributeSpeed.class);
        lookupJsonNameToCmodDeserializer.put("xp", CmodXP.class);
        lookupJsonNameToCmodDeserializer.put("ai_antiair", CmodAITaskBase.class);
        lookupJsonNameToCmodDeserializer.put("ai_mining", CmodAITaskBase.class);
        lookupJsonNameToCmodDeserializer.put("ai_omniscience", CmodAITaskBase.class);
        lookupJsonNameToCmodDeserializer.put("ai_counterattack", CmodAITaskBase.class);
        lookupJsonNameToCmodDeserializer.put("ai_lunge", CmodAITaskBase.class);
        lookupJsonNameToCmodDeserializer.put("ai_infernal", CmodAIInfernal.class);
        lookupJsonNameToCmodDeserializer.put("template", CmodTemplateReference.class);

        lookupJsonNameToConditionDeserializer.clear();
        lookupJsonNameToConditionDeserializer.put("context", ConditionContext.class);
        lookupJsonNameToConditionDeserializer.put("difficulty", ConditionDifficulty.class);
        lookupJsonNameToConditionDeserializer.put("invasion_number", ConditionInvasionNumber.class);
        lookupJsonNameToConditionDeserializer.put("random", ConditionRandom.class);
        lookupJsonNameToConditionDeserializer.put("filter_mobs", ConditionFilterMobs.class);
        lookupJsonNameToConditionDeserializer.put("template", ConditionTemplateReference.class);
    }

    public static DifficultyData getData() {
        return data;
    }

    public static void loadFiles() {
        data.reset();



        if (!ConfigCoroUtil.tempDisableHWInvFeatures) {
            CoroUtil.dbg("Start reading CoroUtil json difficulty files");

            try {

                if (!dataFolder.exists() || dataFolder.listFiles().length <= 0/* || ConfigCoroUtil.forceDDDataClear*/ || isTemplatesUnchanged()) {
                    CULog.log("Detected coroutil json data missing or unchanged from previous generation, generating from templates");
                    generateDataTemplates();
                } else {
                    CULog.dbg("Preserving existing configs as they have been changed since generation");
                }

                if (dataFolder.exists()) {
                    processFolder(dataFolder);
                } else {
                    CULog.err("CRITICAL Error generating data folder");
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            CoroUtil.dbg("done processing difficulty files");
        }
    }

    public static boolean isTemplatesUnchanged() {

        try {
            //if hash file missing, force a regen
            if (!dataHashes.exists()) return true;
            String fileContentsHash = Files.toString(dataHashes, Charsets.UTF_8);
            String[] hashes = fileContentsHash.split("@@@");
            List<File> listFiles = getFiles(dataFolder);
            //if mismatch in file count, consider it modified
            if (hashes.length != listFiles.size()) {
                CoroUtil.dbg("Detected file count mismatch: " + hashes.length + " vs " + listFiles.size());
                return false;
            }
            for (File child : listFiles) {
                String hash = getMD5(child);
                boolean found = false;
                for (String hashTry : hashes) {
                    if (hash.equals(hashTry)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    CoroUtil.dbg("Detected file changed from last template generation: " + child);
                    return false;
                }
            }
            CoroUtil.dbg("Detected no files changed in filesystem, allowing template regen");
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    public static String getMD5(File file) {
        try {
            byte[] buffer= new byte[8192];
            int count;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
            while ((count = bis.read(buffer)) > 0) {
                digest.update(buffer, 0, count);
            }
            bis.close();

            byte[] hash = digest.digest();
            return new BASE64Encoder().encode(hash);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return "";
    }

    public static List<File> getFiles(File file) {
        List<File> listFiles = new ArrayList<>();
        for (File child : file.listFiles()) {
            if (child.isFile()) {
                try {
                    if (child.toString().endsWith(".json")) {
                        listFiles.add(child);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } else {
                listFiles.addAll(getFiles(child));
            }
        }
        return listFiles;
    }

    public static void generateDataTemplates() {

        dataFolder.mkdirs();

        List<String> listFiles = new ArrayList<>();
        List<String> listFileHashes = new ArrayList<>();

        for (ModContainer mod : Loader.instance().getActiveModList()) {

            if (mod.getModId().equals(CoroUtil.modID)) {
                CraftingHelper.findFiles(mod, "assets/" + mod.getModId() + "/config",
                        null, (root, file) ->
                        {
                            //System.out.println("2:" + root + ", " + file);
                            if (file.toString().endsWith("json")) {
                                listFiles.add(file.toString());
                            }
                            return true;
                        }, true, true);
            }
        }

        for (String file : listFiles) {
            listFileHashes.add(copyFileFromJarPath(file));
        }

        String hashFileContents = "";
        for (String hash : listFileHashes) {
            hashFileContents += hash + "@@@"/*System.getProperty("line.separator")*/;
        }

        try {
            FileUtils.writeStringToFile(dataHashes, hashFileContents, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void processFileFromJarPath(String path) {
        try {

            String pathRoot = path.substring(path.indexOf("assets/coroutil/"));

            if (path.contains(lootTablesFolder)) {
                CULog.dbg("processing, detected as loot table: " + path.substring(path.lastIndexOf("/")+1).toString());

                String temp = pathRoot.replace("assets/" + CoroUtil.modID + "/", "");
                String fileContents = UtilClasspath.getContentsFromResourceLocation(new ResourceLocation(CoroUtil.modID, temp));
                CULog.dbg("file contents size: " + fileContents.length());

                String fileName = path.substring(path.lastIndexOf("/")+1).replace(".json", "");
                ResourceLocation resName = new ResourceLocation(CoroUtil.modID + ":loot_tables." + fileName);
                LootTable lootTable = net.minecraftforge.common.ForgeHooks.loadLootTable(LootTableManager.GSON_INSTANCE, resName, fileContents, true, null);
                data.lookupLootTables.put(fileName, lootTable);
            } else {
                CULog.dbg("processing, detected as DifficultyData: " + path.substring(path.lastIndexOf("/")+1).toString());
                String temp = pathRoot.replace("assets/" + CoroUtil.modID + "/", "");
                String fileContents = UtilClasspath.getContentsFromResourceLocation(new ResourceLocation(CoroUtil.modID, temp));
                CULog.dbg("file contents size: " + fileContents.length());

                GSONBuffInventory.fromJson(fileContents, DifficultyData.class);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static String copyFileFromJarPath(String path) {
        String md5 = "";
        try {

            String pathRoot = path.substring(path.indexOf("assets/coroutil/"));
            String pathSub = "assets/coroutil/config";
            String pathRoot2 = path.substring(path.indexOf(pathSub) + pathSub.length());

            String fileContents = "";

            if (path.contains(lootTablesFolder)) {
                //CULog.dbg("processing, detected as loot table: " + path.substring(path.lastIndexOf("/")+1).toString());
                String temp = pathRoot.replace("assets/" + CoroUtil.modID + "/", "");
                fileContents = UtilClasspath.getContentsFromResourceLocation(new ResourceLocation(CoroUtil.modID, temp));
                //CULog.dbg("file contents size: " + fileContents.length());
            } else {
                //CULog.dbg("processing, detected as DifficultyData: " + path.substring(path.lastIndexOf("/")+1).toString());
                String temp = pathRoot.replace("assets/" + CoroUtil.modID + "/", "");
                fileContents = UtilClasspath.getContentsFromResourceLocation(new ResourceLocation(CoroUtil.modID, temp));
                //CULog.dbg("file contents size: " + fileContents.length());
            }

            if (!fileContents.equals("")) {
                File fileOut = new File(dataFolder + pathRoot2);
                CULog.dbg("copying " + path.substring(path.lastIndexOf("/")+1).toString() + " to " + fileOut.toString());
                FileUtils.writeStringToFile(fileOut, fileContents, StandardCharsets.UTF_8);
                md5 = getMD5(fileOut);
            } else {
                CULog.err("couldnt get contents of file: " + path);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return md5;
    }

    public static void processFileFromFilesystem(File file) {
        try {

            //CoroUtil.dbg("processing: " + file.toString());
            String lastPath = file.getParent().toLowerCase();
            if (lastPath.endsWith(lootTablesFolder)) {
                CULog.dbg("processing, detected as loot table: " + file.toString());
                String fileContents = Files.toString(file, Charsets.UTF_8);
                String fileName = file.getName().replace(".json", "");
                ResourceLocation resName = new ResourceLocation(CoroUtil.modID + ":loot_tables." + fileName);
                LootTable lootTable = net.minecraftforge.common.ForgeHooks.loadLootTable(LootTableManager.GSON_INSTANCE, resName, fileContents, true, null);
                data.lookupLootTables.put(fileName, lootTable);
            } else {
                CULog.dbg("processing, detected as DifficultyData: " + file.toString());
                GSONBuffInventory.fromJson(new BufferedReader(new FileReader(file)), DifficultyData.class);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void processFolder(File path) {
        for (File child : path.listFiles()) {
            if (child.isFile()) {
                try {
                    if (child.toString().endsWith(".json")) {
                        processFileFromFilesystem(child);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } else {
                processFolder(child);
            }
        }
    }

}
