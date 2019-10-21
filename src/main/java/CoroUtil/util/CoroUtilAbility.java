package CoroUtil.util;

import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompoundNBT;
import CoroUtil.ability.Ability;

public class CoroUtilAbility {

	public static CompoundNBT nbtSyncWriteAbility(String abilityName, ConcurrentHashMap<String, Ability> abilities, boolean fullSync) {
		CompoundNBT nbt = new CompoundNBT();
		Ability ability = abilities.get(abilityName);
		if (ability != null) {
			nbt = nbtSyncWriteAbility(ability, fullSync);
		} else {
			System.out.println("Error: failed to find ability to sync: " + abilityName);
		}
		return nbt;
	}
	
	public static CompoundNBT nbtSyncWriteAbility(Ability ability, boolean fullSync) {
		CompoundNBT nbt = new CompoundNBT();
		if (fullSync) {
			nbt.setTag(ability.name, ability.nbtSave());
		} else {
			nbt.setTag(ability.name, ability.nbtSyncWrite());
		}
		return nbt;
	}
	
	public static CompoundNBT nbtSyncWriteAbilities(ConcurrentHashMap<String, Ability> abilities) {
		return nbtWriteAbilities(abilities, true);
	}
	
	public static CompoundNBT nbtSaveAbilities(ConcurrentHashMap<String, Ability> abilities) {
		return nbtWriteAbilities(abilities, false);
	}
	
	public static CompoundNBT nbtWriteAbilities(ConcurrentHashMap<String, Ability> abilities, boolean syncOnly) {
		CompoundNBT nbt = new CompoundNBT();
		
		for (Map.Entry<String, Ability> entry : abilities.entrySet()) {
			if (syncOnly) {
				nbt.setTag(entry.get().name, entry.get().nbtSyncWrite());
			} else {
				nbt.setTag(entry.get().name, entry.get().nbtSave());
			}
		}
		return nbt;
	}
	
	public static void nbtLoadSkills(CompoundNBT nbt, ConcurrentHashMap<String, Ability> abilities, LivingEntity owner) {
		nbtLoadSkills(nbt, abilities, owner, false);
	}
	
	/* It will try a full nbt load if it detected the skill wasnt there, but this requires the server to have predicted this and actually sent a full nbtLoad() package */
	public static void nbtLoadSkills(CompoundNBT nbt, ConcurrentHashMap<String, Ability> abilities, LivingEntity owner, boolean syncOnly) {

		Iterator it = nbt.keySet().iterator();
		
		while (it.hasNext()) {
			String tagName = (String) it.next();
			CompoundNBT data = (CompoundNBT)nbt.get(tagName);
			
			String abilityName = data.getString("name");
			Ability ability = null;
			if (syncOnly) {
				ability = abilities.get(abilityName);
			}
			
			boolean abilityWasMissing = false;
			
			if (ability == null) {
				ability = createAbilityFromString(data.getString("classname"));
				abilityWasMissing = true;
			}
			
			if (ability != null) {
				ability.init(owner);
				
				//Hmm...
				if (abilityWasMissing || !syncOnly) {
					if (data.getBoolean("fullSave")) {
						ability.nbtLoad(data);
					} else {
						System.out.println("Abilities error: code tried to do a full nbt load but one is not available, implementation error - " + owner);
						//note to self, 2 causes is:
						//- world mass entity reload causing different load ordering
						//- entities spawned outside of entity tracker range, entity is added to client later, doesnt get packets
						
						//solution 1:
						//- let client side request a full sync
						
						//solution 2:
						//IWorldAccess.onEntityAdded
					}
				}
				if (syncOnly) {
					ability.nbtSyncRead(data);
				}
				
				//if (!syncOnly) {
					if (!abilities.contains(abilityName)) {
						abilities.put(abilityName, ability);
					} else {
						//System.out.println("error: skill exists already");
					}
				//}
			} else {
				System.out.println("critical error reading skill from nbt/list");
			}
		}
	}
	
	public static Ability createAbilityFromString(String parFullClassName) {
		
		try {
			Class createClass = Class.forName(parFullClassName);
			Constructor constructor = createClass.getConstructor();
			Object createObject = constructor.newInstance();
			if (createObject instanceof Ability) {
				return (Ability) createObject;
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		
		return null;
	}
	
}

