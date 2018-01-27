package net.tytonidae.quickstack;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;

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
			StorageInfo results = storeItems(client.getInventory(), getItemLookup(chests));
			sender.sendMessage(
					"Stacked " + results.ITEMS_STORED + " items across " + results.CHESTS_UNIQUE + " chests.");

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

	private StorageInfo storeItems(PlayerInventory inv, Map<ItemStackKey, List<ChestHandler>> lookup)
	{
		if (lookup.size() == 0) // do not attempt storage if no chests around
		{
			return new StorageInfo(0, 0);
		}
		
		for (ItemStack currentItem : inv)
		{
			if (currentItem != null)
			{
				ItemStackKey currentKey = new ItemStackKey(currentItem);
				List<ChestHandler> matchingChestList = lookup.get(currentKey);
				
				if (matchingChestList != null)
				{
					ListIterator<ChestHandler> matchingChests = matchingChestList.listIterator();	
					
					while ((currentItem.getAmount() > 0) && (matchingChests.hasNext()))
					{
						ChestHandler currentHandler = matchingChests.next();
						Inventory chestInv = currentHandler.getChest().getBlockInventory();
						Integer[] stackIndexes = currentHandler.getIndexes();
						int index = 0;
	
						while ((currentItem.getAmount() > 0) && (index < stackIndexes.length))
						{
							ItemStack destItem = chestInv.getItem(stackIndexes[index]);
							int storageSpace = destItem.getMaxStackSize() - destItem.getAmount();
	
							// if another plugin (or bug) created a stack greater than normal, do not touch it
							if (storageSpace < 0)
							{
								index++;
								continue;
							}
							else if (storageSpace >= currentItem.getAmount())
							{
								destItem.setAmount(destItem.getAmount() + currentItem.getAmount());
								//counter += item.getAmount();
								currentItem.setAmount(0); // appears to successfully remove item
							}
							else
							{
								destItem.setAmount(destItem.getMaxStackSize());
								//counter += storageSpace;
								currentItem.setAmount(currentItem.getAmount() - storageSpace);
							
								
							}
							
							index++;
						}
					}
				}
			}
		}
		
		return new StorageInfo(555, 555); //uniqueChests.size());
	}

	private Map<ItemStackKey, List<ChestHandler>> getItemLookup(Chest[] chests)
	{
		Map<ItemStackKey, List<ChestHandler>> lookup = new HashMap<ItemStackKey, List<ChestHandler>>();
		
		// build map to lookup chests containing desired item
		for (Chest currentChest : chests)
		{
			ListIterator<ItemStack> it = currentChest.getBlockInventory().iterator();

			while (it.hasNext())
			{
				ItemStack currentItem = it.next();

				if (currentItem != null)
				{
					ItemStackKey currentKey = new ItemStackKey(currentItem);
					List<ChestHandler> results = lookup.get(currentKey); // list of chests containing this item

					if (results == null) // first time seeing this stack
					{
						results = new ArrayList<ChestHandler>();
						lookup.put(currentKey, results);
						results.add(new ChestHandler(currentChest, it.nextIndex() - 1));
					}
					else
					{
						// store item index in corresponding ChestHandler if it already exists
						boolean stored = false;
						
						for (ChestHandler handler : results)
						{
							if (handler.getChest() == currentChest)
							{
								handler.addIndex(it.nextIndex() - 1);
								stored = true;
								break;
							}
						}
						
						if (!stored) // otherwise, store in new ChestHandler
						{
							results.add(new ChestHandler(currentChest, it.nextIndex() - 1));
						}
					}


				}
			}
		}

		return lookup;
	}

}

// class to hold counts for items moved and chests changed
class StorageInfo
{
	public final int CHESTS_UNIQUE;
	public final int ITEMS_STORED;

	public StorageInfo(int itemsStored, int uniqueChests)
	{
		CHESTS_UNIQUE = uniqueChests;
		ITEMS_STORED = itemsStored;
	}
}

class ItemStackKey
{
	private final ItemStack _item;
	private final int _hash;

	public ItemStackKey(ItemStack item)
	{
		_item = item;
		_hash = Objects.hash(_item.getType(), _item.getMaxStackSize());
	}

	public int hashCode()
	{
		return _hash;
	}

	public boolean equals(Object obj)
	{
		if (!(obj instanceof ItemStackKey))
		{
			return false;
		}
		else
		{
			ItemStackKey toCompare = (ItemStackKey) obj;
			return (_hash == toCompare.hashCode());
		}
	}
}

// class to track a chest and indexes of desired items
class ChestHandler
{
	private final Chest _container;
	private final List<Integer> _indexes;

	public ChestHandler(Chest container, int index)
	{
		_container = container;
		_indexes = new ArrayList<Integer>();
		
		this.addIndex(index);
	}

	public void addIndex(int index)
	{
		_indexes.add(index);
	}

	public Chest getChest()
	{
		return _container;
	}

	public Integer[] getIndexes()
	{
		return _indexes.toArray(new Integer[_indexes.size()]);
	}
}
