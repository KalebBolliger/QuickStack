package net.tytonidae.quickstack;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Set;
import java.util.HashSet;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin
{
	final int CUBE_HALF_SIDE = 5;

	@Override
	public void onEnable()
	{

	}

	@Override
	public void onDisable()
	{

	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
	{
		if (command.getName().equalsIgnoreCase("stack"))
		{
			Player client = (Player) sender;
			Chest[] chests = getChests(client.getLocation());
			StorageInfo results = storeItems(client.getInventory(), chests);
			sender.sendMessage("Stacked " + results.ITEMS_STORED + " items across " + results.CHESTS_UNIQUE + " chests.");
			
			return true;
		}

		return false;
	}

	private Chest[] getChests(Location pos)
	{
		ArrayList<Chest> locs = new ArrayList<Chest>(); // track nearby chests
		Block searchAnchor = pos.getBlock();
		
		// iterate in cube around player to find chests
		for (int x = -CUBE_HALF_SIDE; x < CUBE_HALF_SIDE; x++)
		{
			for (int y = -CUBE_HALF_SIDE; y < CUBE_HALF_SIDE; y++)
			{
				for (int z = -CUBE_HALF_SIDE; z < CUBE_HALF_SIDE; z++)
				{
					Block current = searchAnchor.getRelative(x, y, z);
					if (current.getType() == Material.CHEST)
					{
						locs.add((Chest) current.getState());
					}
				}
			}
		}

		return locs.toArray(new Chest[locs.size()]);
	}

	private StorageInfo storeItems(PlayerInventory inv, Chest[] chests)
	{
		if (chests.length == 0) // do not attempt storage if no chests around
		{
			return new StorageInfo(0, 0);
		}
		
		int total = 0;
		Set<Integer> uniqueChests = new HashSet<Integer>(chests.length); // track chests receiving items

		for (ItemStack currentItem : inv) // attempt to store each item in passed inventory
		{
			int chestIndex = 0;

			// check each chest for valid storage
			while ((currentItem != null) && (currentItem.getAmount() > 0) && (chestIndex < chests.length))
			{
				Inventory currentInv = chests[chestIndex].getBlockInventory();
				int stored = storeItem(currentItem, currentInv);
				total += stored;
				
				if  (stored > 0)
				{
					uniqueChests.add(chestIndex);
				}
				
				chestIndex++;
			}
		}
		return new StorageInfo(total, uniqueChests.size());
	}
	
	private int storeItem(ItemStack item, Inventory destInv)
	{
		int counter = 0;
		
		Material currentItemMat = item.getType();
		int invPos = destInv.first(item.getType()); // material used to ignore stack amount

		if (invPos != -1)
		{
			ListIterator<ItemStack> it = destInv.iterator(invPos);
			
			// attempt to combine with existing stored stacks until none left
			while ((it.hasNext()) && (item.getAmount() > 0))
			{
				ItemStack destItem = it.next();
				
				if ((destItem != null) && (destItem.getType() == currentItemMat))
				{
					int storageSpace = destItem.getMaxStackSize() - destItem.getAmount();
					
					// if another plugin (or bug) created a stack greater than normal, do not touch it
					if (storageSpace < 0)
					{
						continue;
					}
					else if (storageSpace >= item.getAmount())
					{
						destItem.setAmount(destItem.getAmount() + item.getAmount());
						counter += item.getAmount();
						item.setAmount(0); // appears to successfully remove item
					}
					else
					{
						destItem.setAmount(destItem.getMaxStackSize());
						counter += storageSpace;
						item.setAmount(item.getAmount() - storageSpace);
					}
				}
			}
		}
		
		return counter;
	}
}

// class to hold counts for items moved and chests changed
class StorageInfo
{
	public final int CHESTS_UNIQUE;
	public final int ITEMS_STORED;
	
	StorageInfo(int itemsStored, int uniqueChests)
	{
		CHESTS_UNIQUE = uniqueChests;
		ITEMS_STORED = itemsStored;
	}
}
