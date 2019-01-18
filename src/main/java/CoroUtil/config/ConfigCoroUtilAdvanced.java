package CoroUtil.config;

import modconfig.ConfigComment;
import modconfig.IConfigCategory;

import java.io.File;

public class ConfigCoroUtilAdvanced implements IConfigCategory {

	public static String mobSpawnsProfile = "mob_spawns";

	public static int ticksToRepairBlock = 20*60*5;

	public static double digSpeed = 0.01D;

	@ConfigComment("max repair speed will be whatever scheduleBlockUpdate set the update, which is 30 seconds")
	public static boolean repairBlockNextRandomTick = false;

	@ConfigComment("Probably usefull if you want zombie miners get stopped by FTBU claimed chunks for example, but i dont want this behavior by default")
	public static boolean blockBreakingInvokesCancellableEvent = false;

	@Override
	public String getName() {
		return "Advanced";
	}

	@Override
	public String getRegistryName() {
		return "coroutil_advanced";
	}

	@Override
	public String getConfigFileName() {
		return "CoroUtil" + File.separator + getName();
	}

	@Override
	public String getCategory() {
		return getName();
	}

	@Override
	public void hookUpdatedValues() {

	}

}
