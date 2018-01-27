package net.tytonidae.quickstack;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin
{
	final int SEARCH_DISTANCE = 5;
	final int SEARCH_DISTANCE_SQUARED = SEARCH_DISTANCE * SEARCH_DISTANCE;
	final int CHUNK_LENGTH = 16;

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
		
		Chunk currentChunk = pos.getChunk();
		World level = currentChunk.getWorld();
		int xAnchor = currentChunk.getX();
		int zAnchor = currentChunk.getZ();
		
		int chunkRadius = SEARCH_DISTANCE / CHUNK_LENGTH + 1; // translate block search radius to chunks
		Set<Chunk> chunkList = new HashSet<Chunk>();
		
		// get chunks that could be within search radius
		for (int x = xAnchor - chunkRadius; x <= xAnchor + chunkRadius; x++)
		{
			for (int z = zAnchor - chunkRadius; z <= zAnchor + chunkRadius; z++)
			{
				chunkList.add(level.getChunkAt(x, z));
			}
		}
		
		// get TileEntities in each chunk, save the chests
		for (Chunk current : chunkList)
		{
			BlockState[] tileEnts = current.getTileEntities();
			
			for (BlockState currentState : tileEnts)
			{
				// only save chests within search radius
				if ((currentState.getType() == Material.CHEST) && (currentState.getLocation().distanceSquared(pos) <= SEARCH_DISTANCE_SQUARED))
				{
					locs.add((Chest) currentState);
				}
			}
		}

		return locs.toArray(new Chest[locs.size()]);
	}
	
	// builds an item lookup map and stores item indexes in chests
	private Map<ItemStackKey, List<ChestHandler>> getItemLookup(Chest[] chests)
	{
		Map<ItemStackKey, List<ChestHandler>> lookup = new HashMap<ItemStackKey, List<ChestHandler>>();

		// take note of items from each chest
		for (Chest currentChest : chests)
		{
			ListIterator<ItemStack> it = currentChest.getBlockInventory().iterator();

			while (it.hasNext())
			{
				ItemStack currentItem = it.next();

				if (currentItem != null)
				{
					// ItemStackKey: wrapper class used for comparing items
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
	
	private StorageInfo storeItems(PlayerInventory inv, Map<ItemStackKey, List<ChestHandler>> lookup)
	{
		if (lookup.size() == 0) // do not attempt storage if no chests around
		{
			return new StorageInfo(0, 0);
		}

		int itemCounter = 0;
		Set<Chest> uniqueChests = new HashSet<Chest>(); // used for chest counter message

		for (ItemStack currentItem : inv)
		{
			if (currentItem != null)
			{
				// look for stored versions of this item
				ItemStackKey currentKey = new ItemStackKey(currentItem);
				List<ChestHandler> matchingChestList = lookup.get(currentKey);

				if (matchingChestList != null)
				{
					ListIterator<ChestHandler> matchingChests = matchingChestList.listIterator();

					// terminate if out of item, or if no more chests
					while ((currentItem.getAmount() > 0) && (matchingChests.hasNext()))
					{
						ChestHandler currentHandler = matchingChests.next();
						Inventory chestInv = currentHandler.getChest().getBlockInventory();

						Integer[] stackIndexes = currentHandler.getIndexes(); // indexes of matching items in chest
						int currentIndex = 0;

						// terminate if out of item or indexes for this chest
						while ((currentItem.getAmount() > 0) && (currentIndex < stackIndexes.length))
						{
							ItemStack destItem = chestInv.getItem(stackIndexes[currentIndex]);
							int storageSpace = destItem.getMaxStackSize() - destItem.getAmount();

							// if another plugin (or bug) created a stack greater than normal, do not touch it
							if (storageSpace < 0)
							{
								currentIndex++;
								continue;
							}
							else if (storageSpace >= currentItem.getAmount())
							{
								destItem.setAmount(destItem.getAmount() + currentItem.getAmount());
								itemCounter += currentItem.getAmount();
								currentItem.setAmount(0); // appears to successfully remove item

								uniqueChests.add(currentHandler.getChest());
							}
							else if (storageSpace > 0)
							{
								destItem.setAmount(destItem.getMaxStackSize());
								itemCounter += storageSpace;
								currentItem.setAmount(currentItem.getAmount() - storageSpace);

								uniqueChests.add(currentHandler.getChest());
							}

							currentIndex++;
						}
					}
				}
			}
		}

		return new StorageInfo(itemCounter, uniqueChests.size());
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

// wrapper class used for comparing ItemStacks for stacking purposes
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

// class to track a chest and indexes of desired item
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

	// add an index of the item this Handler is concerned with
	public void addIndex(int index)
	{
		_indexes.add(index);
	}

	// get the chest that this Handler references
	public Chest getChest()
	{
		return _container;
	}

	// get the indexes from this Handler's chest of the relevant item
	public Integer[] getIndexes()
	{
		return _indexes.toArray(new Integer[_indexes.size()]);
	}
}
