package CoroUtil.forge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import CoroUtil.config.ConfigDynamicDifficulty;
import CoroUtil.difficulty.BuffedLocation;
import CoroUtil.difficulty.DynamicDifficulty;
import CoroUtil.difficulty.UtilEntityBuffs;
import CoroUtil.util.*;
import CoroUtil.world.WorldDirector;
import CoroUtil.world.WorldDirectorManager;
import CoroUtil.world.location.ISimulationTickable;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import CoroUtil.OldUtil;
import CoroUtil.pathfinding.PFQueue;
import CoroUtil.quest.PlayerQuestManager;
import CoroUtil.quest.PlayerQuests;
import CoroUtil.quest.quests.ActiveQuest;
import CoroUtil.quest.quests.ItemQuest;

public class CommandCoroUtil extends CommandBase {

	@Override
	public String getCommandName() {
		return "coroutil";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender var1, String[] var2) {
		
		EntityPlayer player = null;
		if (var1 instanceof EntityPlayer) {
			player = (EntityPlayer) var1;
		}
		World world = var1.getEntityWorld();
		int dimension = world.provider.getDimension();
		BlockPos posBlock = var1.getPosition();
		Vec3d posVec = var1.getPositionVector();
		
		try {
			/*if(var1 instanceof EntityPlayerMP)
			{*/
				//EntityPlayer player = getCommandSenderAsPlayer(var1);
				
				if (var2[0].equals("testquest") && player != null) {
					String createQuestStr = "CoroUtil.quest.quests.ItemQuest";
					PlayerQuests plQuests = PlayerQuestManager.i().getPlayerQuests(player);
					ActiveQuest aq = PlayerQuests.createQuestFromString(createQuestStr);

					System.out.println("trying to create quest from str: " + createQuestStr);

					if (aq != null) {
						aq.initCreateObject(plQuests);

						aq.initFirstTime(dimension);
						((ItemQuest) aq).initCustomData(CoroUtilItem.getNameByItem(Items.DIAMOND), 5, false);

						PlayerQuestManager.i().getPlayerQuests(CoroUtilEntity.getName(player)).questAdd(aq);
						System.out.println("create success type: " + aq.questType);
					} else {
						System.out.println("failed to create quest " + createQuestStr);
					}

					plQuests.saveAndSyncPlayer();
				} else if (var2[0].equals("buffloc")) {
					int distRadius = 32;
					float difficulty = 2;
					if (var2.length > 1) {
						difficulty = Float.valueOf(var2[1]);
					}
					ISimulationTickable zone = WorldDirectorManager.instance().getCoroUtilWorldDirector(world).getTickingSimulationByLocation(new BlockCoord(posBlock));
					if (zone == null) {
						DynamicDifficulty.buffLocation(world, new BlockCoord(posBlock), distRadius, difficulty);
						System.out.println("buffed zone at " + posBlock);
					} else {
						System.out.println("buffed zone already at " + posBlock);
					}
				} else if (var2[0].equals("buffremove")) {
					boolean removeAll = false;
					if (var2.length > 1) {
						removeAll = var2[1].equalsIgnoreCase("all");
					}
					WorldDirector wd = WorldDirectorManager.instance().getCoroUtilWorldDirector(world);
					if (removeAll) {
						Iterator<ISimulationTickable> it = wd.listTickingLocations.iterator();
						while(it.hasNext()) {
							ISimulationTickable loc = it.next();
							if (loc instanceof BuffedLocation) {
								wd.removeTickingLocation(loc, true);
								it.remove();
								System.out.println("removed buffed zone at " + loc.getOrigin());
							}
						}

					} else {
						ISimulationTickable zone = wd.getTickingSimulationByLocation(new BlockCoord(posBlock));
						if (zone != null) {
							wd.removeTickingLocation(zone);
							System.out.println("removed buffed zone at " + posBlock);
						} else {
							System.out.println("cant find buffed zone at " + posBlock);
						}
					}

				} else if (var2[0].equals("aitest")) {
					/*System.out.println("AI TEST MODIFY!");
					BehaviorModifier.test(world, Vec3.createVectorHelper(player.posX, player.posY, player.posZ), CoroUtilEntity.getName(player));*/
					
					/*TaskDigTowardsTarget task = new TaskDigTowardsTarget();
					
					System.out.println("ENHANCE!");
					BehaviorModifier.enhanceZombiesToDig(DimensionManager.getWorld(0), new Vec3(player.posX, player.posY, player.posZ), new Class[] { TaskDigTowardsTarget.class, TaskCallForHelp.class }, 5, 0.8F);*/
				} else if (var2[0].equalsIgnoreCase("spawn") && player != null) {
					
					String prefix = "";
					String mobToSpawn = var2[1];
					
					int count = 1;
					
					if (var2.length > 2) {
						count = Integer.valueOf(var2[2]);
					}

					for (int i = 0; i < count; i++) {
						Entity ent = EntityList.createEntityByName(prefix + mobToSpawn, world);
						
						if (ent == null) ent = EntityList.createEntityByName(mobToSpawn, world);
						
						if (ent == null) {
							List<String> entsToSpawn = listEntitiesSpawnable(mobToSpawn);
							if (entsToSpawn.size() > 0) {
								for (int j = 0; j < entsToSpawn.size(); j++) {
									Entity ent2 = EntityList.createEntityByName(entsToSpawn.get(j), world);
									if (ent2 != null) {
										CoroUtilMisc.sendCommandSenderMsg(player, "spawned: " + CoroUtilEntity.getName(ent2));
										spawnEntity(player, ent2);
									}
								}
							} else {
								CoroUtilMisc.sendCommandSenderMsg(player, "found nothing to spawn");
							}
						} else {
							if (ent != null) {
								
								CoroUtilMisc.sendCommandSenderMsg(player, "spawned: " + CoroUtilEntity.getName(ent));
								spawnEntity(player, ent);
								
							}
						}
					}
				} else if (var2[0].equalsIgnoreCase("get")) {
	        		if (var2[1].equalsIgnoreCase("count")) {
	        			boolean exact = false;
	        			if (var2.length > 3) exact = var2[3].equals("exact");
	        			CoroUtilMisc.sendCommandSenderMsg(var1, var2[2] + " count: " + getEntityCount(var2[2], false, exact, dimension));
	        		} else if (var2[1].equalsIgnoreCase("PFQueue")) {
	        			if (var2[2].equalsIgnoreCase("lastpf")) {
	        				CoroUtilMisc.sendCommandSenderMsg(var1, "last PF Time: " + ((System.currentTimeMillis() - PFQueue.lastSuccessPFTime) / 1000F));
	        				//var1.sendChatToPlayer(var2[2] + " set to: " + c_CoroAIUtil.getPrivateValueBoth(PFQueue.class, PFQueue.instance, var2[2], var2[2]));
	        			} if (var2[2].equalsIgnoreCase("stats")) {
	        				CoroUtilMisc.sendCommandSenderMsg(var1, "PFQueue Stats");
	        				CoroUtilMisc.sendCommandSenderMsg(var1, "-------------");
	        				CoroUtilMisc.sendCommandSenderMsg(var1, PFQueue.lastQueueSize + " - " + "PF queue size");
	        				CoroUtilMisc.sendCommandSenderMsg(var1, PFQueue.lastChunkCacheCount + " - " + "Cached chunks");
	        				CoroUtilMisc.sendCommandSenderMsg(var1, PFQueue.statsPerSecondPath + " - " + "Pathfinds / 10 sec");
	        				CoroUtilMisc.sendCommandSenderMsg(var1, PFQueue.statsPerSecondPathSkipped + " - " + "Old PF Skips / 10 sec");
	        				CoroUtilMisc.sendCommandSenderMsg(var1, PFQueue.statsPerSecondNodeMaxIter + " - " + "Big PF Skips / 10 sec");
	        				CoroUtilMisc.sendCommandSenderMsg(var1, PFQueue.statsPerSecondNode + " - " + "Nodes ran / 10 sec");
	        					        				
	        				
	        				
	        			} else {
	        				//var1.sendChatToPlayer("Last chunk cache count: " + PFQueue.lastChunkCacheCount);
	        				CoroUtilMisc.sendCommandSenderMsg(var1, var2[2] + " set to: " + OldUtil.getPrivateValueBoth(PFQueue.class, PFQueue.instance, var2[2], var2[2]));
	        			}
	        		}
	        	} else if (var2[0].equalsIgnoreCase("kill")) {
	        		boolean exact = false;
	        		//int dim = world.provider.getDimension();
        			//if (var2.length > 2) exact = var2[2].equals("exact");
	        		if (var2.length > 2) dimension = Integer.valueOf(var2[1]);
	        		CoroUtilMisc.sendCommandSenderMsg(var1, var2[1] + " count killed: " + getEntityCount(var2[1], true, exact, dimension));
	        	} else if (var2[0].equalsIgnoreCase("list")) {
	        		String param = null;
	        		//int dim = world.provider.getDimension();
	        		
	        		String fullCommand = "";
	        		for (String entry : var2) {
	        			fullCommand += entry + " ";
	        		}
	        		boolean simple = false;
	        		if (fullCommand.contains(" simple")) {
	        			simple = true;
	        		} else {
	        			if (var2.length > 1) dimension = Integer.valueOf(var2[1]);
		        		if (var2.length > 2) param = var2[2];
	        		}
	        		HashMap<String, Integer> entNames = listEntities(param, dimension, simple);
	                
	        		CoroUtilMisc.sendCommandSenderMsg(var1, "List for dimension id: " + dimension);
	        		
	                Iterator it = entNames.entrySet().iterator();
	                while (it.hasNext()) {
	                    Map.Entry pairs = (Map.Entry)it.next();
	                    CoroUtilMisc.sendCommandSenderMsg(var1, pairs.getKey() + " = " + pairs.getValue());
	                    //System.out.println(pairs.getKey() + " = " + pairs.getValue());
	                    it.remove();
	                }
	        	} else if (var2[0].equalsIgnoreCase("location")) {
	        		String param = null;
	        		//int dim = world.provider.getDimension();
	        		int indexStart = 0;
	        		
	        		String fullCommand = "";
	        		for (String entry : var2) {
	        			fullCommand += entry + " ";
	        		}
	        		boolean simple = true;
	        		/*if (fullCommand.contains(" simple")) {
	        			simple = true;
	        		} else {*/
	        			//using index start instead of dimension
	        			if (var2.length > 1) indexStart = Integer.valueOf(var2[1]);
		        		if (var2.length > 2) param = var2[2];
	        		//}
	        		List<String> data = listEntitiesLocations(param, dimension, simple, indexStart);
	                
	        		CoroUtilMisc.sendCommandSenderMsg(var1, "Location list for dimension id: " + dimension);
	        		for (String entry : data) {
	        			CoroUtilMisc.sendCommandSenderMsg(var1, entry);
	        		}
				} else if (var2[0].equalsIgnoreCase("difficulty") || var2[0].equalsIgnoreCase("diff")) {
					if ((var1 instanceof EntityPlayerMP)) {
						EntityPlayerMP ent = (EntityPlayerMP) var1;
						//net.minecraft.util.Vec3 posVec = ent.getPosition(1F);
						net.minecraft.util.math.Vec3d posVec2 = new net.minecraft.util.math.Vec3d(ent.posX, ent.posY + (ent.getEyeHeight() - ent.getDefaultEyeHeight()), ent.posZ);//player.getPosition(1F);
						BlockCoord pos = new BlockCoord(MathHelper.floor_double(posVec2.xCoord), MathHelper.floor_double(posVec2.yCoord), MathHelper.floor_double(posVec2.zCoord));
						//long dayNumber = (ent.worldObj.getWorldTime() / 24000) + 1;
						CoroUtilMisc.sendCommandSenderMsg(ent, "Difficulties for you: ");
						CoroUtilMisc.sendCommandSenderMsg(ent, "player rating: " + DynamicDifficulty.getDifficultyScaleForPlayerEquipment(ent) + " weight: " + ConfigDynamicDifficulty.weightPlayerEquipment);
						CoroUtilMisc.sendCommandSenderMsg(ent, "server time: " + DynamicDifficulty.getDifficultyScaleForPlayerServerTime(ent) + " weight: " + ConfigDynamicDifficulty.weightPlayerServerTime);
						CoroUtilMisc.sendCommandSenderMsg(ent, "avg chunk time: " + DynamicDifficulty.getDifficultyScaleForPosOccupyTime(ent.worldObj, pos) + " weight: " + ConfigDynamicDifficulty.weightPosOccupy);
						CoroUtilMisc.sendCommandSenderMsg(ent, "best dps: " + DynamicDifficulty.getDifficultyScaleForPosDPS(ent.worldObj, pos) + " weight: " + ConfigDynamicDifficulty.weightDPS);
						CoroUtilMisc.sendCommandSenderMsg(ent, "health: " + DynamicDifficulty.getDifficultyScaleForHealth(ent) + " weight: " + ConfigDynamicDifficulty.weightHealth);
						CoroUtilMisc.sendCommandSenderMsg(ent, "dist from spawn: " + DynamicDifficulty.getDifficultyScaleForDistFromSpawn(ent) + " weight: " + ConfigDynamicDifficulty.weightDistFromSpawn);
						CoroUtilMisc.sendCommandSenderMsg(ent, "buffed location: " + DynamicDifficulty.getDifficultyForBuffedLocation(world, pos) + " weight: " + ConfigDynamicDifficulty.weightBuffedLocation);
						CoroUtilMisc.sendCommandSenderMsg(ent, "debuffed location: " + DynamicDifficulty.getDifficultyForDebuffedLocation(world, pos) + " weight: " + ConfigDynamicDifficulty.weightDebuffedLocation);
						CoroUtilMisc.sendCommandSenderMsg(ent, "invasion buff: " + DynamicDifficulty.getInvasionSkipBuff(ent));
						CoroUtilMisc.sendCommandSenderMsg(ent, "------------");
						CoroUtilMisc.sendCommandSenderMsg(ent, "average: " + DynamicDifficulty.getDifficultyScaleAverage(ent.worldObj, ent, pos));
					}
				} else if (var2[0].equalsIgnoreCase("testitem")) {
					/**
					 * ResourceLocation resourcelocation = new ResourceLocation(id);
					 Item item = (Item)Item.REGISTRY.getObject(resourcelocation);
					 */
					Item item = Item.getByNameOrId("particleman:particleglove");
					if (item != null) {
						System.out.println("! " + item.getRegistryName().toString());
					}
					if (player != null) {
						player.inventory.addItemStackToInventory(new ItemStack(item, 1));
					}
				}
			/*}*/
		} catch (Exception ex) {
			System.out.println("Exception handling CoroUtil command");
			ex.printStackTrace();
		}
		
	}
	
	public void spawnEntity(EntityPlayer player, Entity ent) {
		double dist = 1D;
		
		double finalX = player.posX - (Math.sin(player.rotationYaw * 0.01745329F) * dist);
		double finalZ = player.posZ + (Math.cos(player.rotationYaw * 0.01745329F) * dist);
		
		double finalY = player.posY;
		
		ent.setPosition(finalX, finalY, finalZ);
		
		
		
		//temp
		//ent.setPosition(69, player.worldObj.getHeightValue(69, 301), 301);
		//((JobGroupHorde)((ICoroAI) ent).getAIAgent().jobMan.priJob).attackCoord = new BlockCoord(44, player.worldObj.getHeightValue(44, 301), 301);
		
		player.worldObj.spawnEntityInWorld(ent);
		//if (ent instanceof EntityLiving) ((EntityLiving)ent).onSpawnWithEgg(null); //moved to after spawn, so client has an entity at least before syncs fire
		if (ent instanceof EntityLiving) ((EntityLiving)ent).onInitialSpawn(player.worldObj.getDifficultyForLocation(new BlockPos(ent)), null);
		//if (ent instanceof ICoroAI) ((ICoroAI) ent).getAIAgent().spawnedOrNBTReloadedInit();

		//TEST
		/*if (ent instanceof EntityLivingBase) {
			String listMods = "";
			for (String mod : CoroUtilCrossMod.listModifiers) {
				listMods += mod + " ";
			}
			CoroUtilCrossMod.infernalMobs_AddModifiers((EntityLivingBase)ent, listMods);


		}*/
	}
	
	public List<String> listEntitiesSpawnable(String entName) {
		List<String> entNames = new ArrayList<String>();
        
		Iterator it = EntityList.NAME_TO_CLASS.keySet().iterator();
		
		while (it.hasNext()) {
			String entry = (String) it.next();
			
			if (entry.toLowerCase().contains(entName.toLowerCase())) {
				entNames.add(entry);
			}
		}
		
		return entNames;
	}
	
	public HashMap<String, Integer> listEntities(String entName, int dim, boolean simpleNames) {
		HashMap<String, Integer> entNames = new HashMap<String, Integer>();
        
		World world = DimensionManager.getWorld(dim);
		
        for (int var33 = 0; var33 < world.loadedEntityList.size(); ++var33)
        {
            Entity ent = (Entity)world.loadedEntityList.get(var33);
            
            String entClass = ent.getClass().getCanonicalName();
            
            if (simpleNames) {
            	entClass = EntityList.getEntityString(ent);
            }
            
            if (entClass != null && (entName == null || /*EntityList.getEntityString(ent)*/entClass.toLowerCase().contains(entName.toLowerCase()))) {
	            int val = 1;
	            
	            if (entNames.containsKey(entClass)) {
	            	val = entNames.get(entClass)+1;
	            }
	            entNames.put(entClass, val);
            }
            
        }
        
        //entNames.put("Total count: ", world.loadedEntityList.size());
        
        return entNames;
	}
	
	public List<String> listEntitiesLocations(String entName, int dim, boolean simpleNames, int indexStart) {
		//HashMap<String, Integer> entNames = new HashMap<String, Integer>();
		List<String> listData = new ArrayList<String>();
        
		World world = DimensionManager.getWorld(dim);
		
		int matches = 0;
		
        for (int var33 = 0; var33 < world.loadedEntityList.size(); ++var33)
        {
            Entity ent = (Entity)world.loadedEntityList.get(var33);
            
            String entClass = ent.getClass().getCanonicalName();
            
            if (simpleNames) {
            	entClass = EntityList.getEntityString(ent);
            }
            
            if (entClass != null && (entName == null || /*EntityList.getEntityString(ent)*/entClass.toLowerCase().contains(entName.toLowerCase()))) {
            	
            	if (indexStart <= matches) {
            		listData.add("pos: " + MathHelper.floor_double(ent.posX) + ", " + MathHelper.floor_double(ent.posY) + ", " + MathHelper.floor_double(ent.posZ) + ", " + entClass);
            		if (listData.size() >= 10) {
            			return listData;
            		}
            	}
            	
	            /*int val = 1;
	            
	            if (entNames.containsKey(entClass)) {
	            	val = entNames.get(entClass)+1;
	            }
	            entNames.put(entClass, val);*/
            	matches++;
            }
            
        }
        
        //entNames.put("Total count: ", world.loadedEntityList.size());
        
        return listData;
	}
	
	public int getEntityCount(String entName, boolean killEntities, boolean exact, int dim) {
		int count = 0;
		
        for (int var33 = 0; var33 < DimensionManager.getWorld(dim).loadedEntityList.size(); ++var33)
        {
            Entity ent = (Entity)DimensionManager.getWorld(dim).loadedEntityList.get(var33);
            
            if (EntityList.getEntityString(ent) != null && (EntityList.getEntityString(ent).equals(entName) || (!exact && EntityList.getEntityString(ent).toLowerCase().contains(entName.toLowerCase())))) {
            	count++;
            	if (killEntities) {
            		//ent.attackEntityFrom(DamageSource.generic, 60);
            		ent.setDead();
            	}
            }
        }
        
        return count;
	}
	
	@Override
	public boolean checkPermission(MinecraftServer server, ICommandSender par1ICommandSender)
    {
        return par1ICommandSender.canCommandSenderUseCommand(this.getRequiredPermissionLevel(), this.getCommandName());
    }

	@Override
	public String getCommandUsage(ICommandSender icommandsender) {
		return "Magic dev method!";
	}
	
	@Override
	public int getRequiredPermissionLevel() {
		return 2;
	}

}
