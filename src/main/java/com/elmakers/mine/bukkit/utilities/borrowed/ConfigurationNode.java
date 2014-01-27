package com.elmakers.mine.bukkit.utilities.borrowed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import com.elmakers.mine.bukkit.blocks.BlockData;
import com.elmakers.mine.bukkit.utilities.CSVParser;

/**
 * Represents a configuration node.
 */
public class ConfigurationNode {
	protected Map<String, Object> root;
	protected static CSVParser csv = new CSVParser();

	@SuppressWarnings("unchecked")
	public ConfigurationNode createChild(String name)
	{
		Map<String, Object> newChild = new HashMap<String, Object>();

		setProperty(name, newChild);

		Object raw = getProperty(name);

		if (raw instanceof Map) 
		{
			return new ConfigurationNode((Map<String, Object>) raw);
		}

		return null;
	}

	public ConfigurationNode() {
		this.root = new HashMap<String, Object>();
	}

	public ConfigurationNode(ConfigurationNode copy) {
		HashMap<String, Object> newRoot = new HashMap<String, Object>();
		if (copy != null)
		{
			newRoot.putAll(copy.root);
		}
		this.root = newRoot;
	}

	public ConfigurationNode(Map<String, Object> root) {
		this.root = root;
	}

	/**
	 * Gets all of the cofiguration values within the Node as
	 * a key value pair, with the key being the full path and the
	 * value being the Object that is at the path.
	 *
	 * @return A map of key value pairs with the path as the key and the object as the value
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> getAll() {
		Map<String, Object> map = new TreeMap<String, Object>();

		Set<String> keys = root.keySet();
		for( String k : keys ) {
			Object tmp = root.get(k);
			if( tmp instanceof Map<?,?> ) {
				Map<String, Object> rec = recursiveBuilder((Map <String,Object>) tmp);

				Set<String> subkeys = rec.keySet();
				for( String sk : subkeys ) {
					map.put(k + "." + sk, rec.get(sk));
				}
			}
			else {
				map.put(k, tmp);
			}
		}

		return map;
	}

	/**
	 * A helper method for the getAll method that deals with the recursion
	 * involved in traversing the tree
	 *
	 * @param node The map for that node of the tree
	 * @return The fully pathed map for that point in the tree, with the path as the key
	 */
	@SuppressWarnings("unchecked")
	protected Map<String, Object> recursiveBuilder(Map<String, Object> node) {
		Map<String, Object> map = new TreeMap<String, Object>();

		Set<String> keys = node.keySet();
		for( String k : keys ) {
			Object tmp = node.get(k);
			if( tmp instanceof Map<?,?> ) {
				Map<String, Object> rec = recursiveBuilder((Map <String,Object>) tmp);

				Set<String> subkeys = rec.keySet();
				for( String sk : subkeys ) {
					map.put(k + "." + sk, rec.get(sk));
				}
			}
			else {
				map.put(k, tmp);
			}
		}

		return map;
	}

	/**
	 * Gets a property at a location. This will either return an Object
	 * or null, with null meaning that no configuration value exists at
	 * that location. This could potentially return a default value (not yet
	 * implemented) as defined by a plugin, if this is a plugin-tied
	 * configuration.
	 *
	 * @param path path to node (dot notation)
	 * @return object or null
	 */
	@SuppressWarnings("unchecked")
	public Object getProperty(String path) {
		// More nasty special cases since float keys look like paths! :\
		boolean isFloat = false;
		try {
			Double.parseDouble(path);
			isFloat = true;
		} catch (Exception ex2) {
		}
		if (isFloat || !path.contains(".")) {
			Object val = root.get(path);

			if (val == null) {
				// Special cases for YAML parsing doing stupid things	
				try {
					return root.get(Integer.parseInt(path));
				} catch (Exception ex) {
					try {
						return root.get(Double.parseDouble(path));
					} catch (Exception ex2) {
						try {
							return root.get(Float.parseFloat(path));
						} catch (Exception ex3) {
							return null;
						}
					}
				}
			}
			return val;
		}

		String[] parts = path.split("\\.");
		Map<String, Object> node = root;

		for (int i = 0; i < parts.length; i++) {
			Object o = node.get(parts[i]);

			if (o == null) {
				return null;
			}

			if (i == parts.length - 1) {
				return o;
			}

			try {
				node = (Map<String, Object>) o;
			} catch (ClassCastException e) {
				return null;
			}
		}

		return null;
	}
	
	public static String fromLocation(Location location) {
		return location.getX() + "," + location.getY() + "," + location.getZ() + "," + location.getWorld().getName();
	}

	/**
	 * Set the property at a location. This will override existing
	 * configuration data to have it conform to key/value mappings.
	 *
	 * @param path
	 * @param value
	 */
	@SuppressWarnings({ "unchecked", "deprecation" })
	public void setProperty(String path, Object value) {
		// Going to convert materials to strings, to make this easier to hand edit.
		if (value instanceof Material)
		{
			Material matValue = (Material)value;
			value = matValue.name().toLowerCase();
		}
		
		// Convert locations
		if (value instanceof Location)
		{
			value = fromLocation((Location)value);
		}

		// Convert blocks
		if (value instanceof BlockData)
		{
			BlockData blockValue = (BlockData)value;
			value = blockValue.toString();
		}
		if (value instanceof Block)
		{
			Block blockValue = (Block)value;
			value = fromLocation(blockValue.getLocation()) + "|" + blockValue.getTypeId() + ":" + blockValue.getData();
		}

		if (!path.contains(".")) {
			root.put(path, value);
			return;
		}

		String[] parts = path.split("\\.");
		Map<String, Object> node = root;

		for (int i = 0; i < parts.length; i++) {
			Object o = node.get(parts[i]);

			// Found our target!
			if (i == parts.length - 1) {
				node.put(parts[i], value);
				return;
			}

			if (o == null || !(o instanceof Map)) {
				// This will override existing configuration data!
				o = new HashMap<String, Object>();
				node.put(parts[i], o);
			}

			node = (Map<String, Object>) o;
		}
	}

	/**
	 * Gets a string at a location. This will either return an String
	 * or null, with null meaning that no configuration value exists at
	 * that location. If the object at the particular location is not actually
	 * a string, it will be converted to its string representation.
	 *
	 * @param path path to node (dot notation)
	 * @return string or null
	 */
	public String getString(String path) {
		Object o = getProperty(path);

		if (o == null) {
			return null;
		}
		return o.toString();
	}

	public Material getMaterial(String path) {
		Object o = getProperty(path);
		if (o == null) {
			return null;
		}

		return toMaterial(o);
	}
	
	public MaterialAndData getMaterialAndData(String path) {
		Object o = getProperty(path);
		if (o == null) {
			return null;
		}

		return toMaterialAndData(o);
	}
	
	public Location getLocation(String path) {
		Object o = getProperty(path);
		if (o == null) {
			return null;
		}

		return toLocation(o);
	}
	
	public static Location toLocation(Object o) {
		if (o instanceof Location) {
			return (Location)o;
		}
		if (o instanceof String) {
			try {
				String[] pieces = StringUtils.split((String)o, ',');
				double x = Double.parseDouble(pieces[0]);
				double y = Double.parseDouble(pieces[1]);
				double z = Double.parseDouble(pieces[2]);
				World world = null;
				if (pieces.length > 3) {
					world = Bukkit.getWorld(pieces[3]);
				} else {
					world = Bukkit.getWorlds().get(0);
				}
				return new Location(world, x, y, z);
			} catch(Exception ex) {
				return null;
			}
		}
		return null;
	}

	@SuppressWarnings("deprecation")
	public static Material toMaterial(Object o)
	{
		if (o instanceof Material) {
			return (Material)o;
		}
		if (o instanceof Integer) {
			return Material.values()[(Integer)o];
		}
		if (o instanceof String) {
			String matName = (String)o;
			try
			{
				Integer value = Integer.parseInt(matName);
				if (value != null)
				{
					return Material.getMaterial(value);
				}
			}
			catch(NumberFormatException ex)
			{

			}
			return Material.getMaterial(matName.toUpperCase());
		}

		return null;
	}

	@SuppressWarnings("deprecation")
	public static MaterialAndData toMaterialAndData(Object o)
	{
		if (o instanceof MaterialAndData) {
			return (MaterialAndData)o;
		}
		if (o instanceof String) {
			String matName = (String)o;
			Material material = null;
			byte data = 0;
			String[] pieces = StringUtils.split(matName, ':');
			if (pieces.length > 1) {
				try {
					data = Byte.parseByte(pieces[1]);
				}
				catch(NumberFormatException ex)
				{
					data = 0;
				}
			}
			try
			{
				Integer value = Integer.parseInt(pieces[0]);
				if (value != null)
				{
					material = Material.getMaterial(value);
				}
			}
			catch(NumberFormatException ex)
			{
				material = Material.getMaterial(pieces[0].toUpperCase());
			}
			
			if (material == null) return null;
			return new MaterialAndData(material, data);
		}

		return null;
	}
	
	public MaterialAndData getMaterialAndData(String path, Material def) {
		return getMaterialAndData(path, def, (byte)0);
	}
	
	public MaterialAndData getMaterialAndData(String path, Material def, byte defData) {
		MaterialAndData o = getMaterialAndData(path);
		return o == null ? new MaterialAndData(def, defData) : o;
	}

	public Material getMaterial(String path, Material def) {
		Material o = getMaterial(path);

		if (o == null) {
			setProperty(path, def);
			return def;
		}

		return o;
	}

	/**
	 * Gets a string at a location. This will either return an String
	 * or the default value. If the object at the particular location is not
	 * actually a string, it will be converted to its string representation.
	 *
	 * @param path path to node (dot notation)
	 * @param def default value
	 * @return string or default
	 */
	 public String getString(String path, String def) {
		 String o = getString(path);

		 if (o == null) {
			 setProperty(path, def);
			 return def;
		 }
		 return o;
	 }

	 /**
	  * Gets an integer at a location. This will either return an integer
	  * or the default value. If the object at the particular location is not
	  * actually a integer, the default value will be returned. However, other
	  * number types will be casted to an integer.
	  *
	  * @param path path to node (dot notation)
	  * @param def default value
	  * @return int or default
	  */
	 public int getInt(String path, int def) {
		 Integer o = castInt(getProperty(path));

		 if (o == null) {
			 setProperty(path, def);
			 return def;
		 } else {
			 return o;
		 }
	 }
	 
	 public Integer getInteger(String path, Integer def) {
		 Integer o = castInt(getProperty(path));

		 if (o == null) {
			 setProperty(path, def);
			 return def;
		 } else {
			 return o;
		 }
	 }

	 public int getInteger(String path, int def) {
		 return getInt(path, def);
	 }
	 
	 public long getLong(String path, long def) {
		 Long o = castLong(getProperty(path));

		 if (o == null) {
			 setProperty(path, def);
			 return def;
		 } else {
			 return o;
		 }
	 }

	 public Set<Material> getMaterials(String key, String csvList)
	 {
		 List<String> defaultMatNames = csv.parseStrings(csvList);
		 List<String> materialData = getStringList(key, defaultMatNames);
		 Set<String> matNames = new HashSet<String>();
		 Set<Material> materials = new HashSet<Material>();

		 for (String matName : materialData)
		 {
			 Material material = toMaterial(matName);
			 if (material != null) {
				 materials.add(material);
				 matNames.add(material.name().toLowerCase());
			 }
		 }

		 setProperty(key, matNames);

		 return materials;
	 }

	 public Set<Material> getMaterials(String key, Collection<Material> def)
	 {
		 List<String> defaultMatNames = new ArrayList<String>();
		 for (Material mat : def)
		 {
			 defaultMatNames.add(mat.name().toLowerCase());
		 }
		 List<String> materialData = getStringList(key, defaultMatNames);
		 
		 Set<String> matNames = new HashSet<String>();
		 Set<Material> materials = new HashSet<Material>();

		 for (String matName : materialData)
		 {
			 Material material = toMaterial(matName);
			 if (material != null) {
				 materials.add(material);
				 matNames.add(material.name().toLowerCase());
			 }
		 }

		 setProperty(key, matNames);

		 return materials;
	 }

	 /**
	  * Gets a double at a location. This will either return an double
	  * or the default value. If the object at the particular location is not
	  * actually a double, the default value will be returned. However, other
	  * number types will be casted to an double.
	  *
	  * @param path path to node (dot notation)
	  * @param def default value
	  * @return double or default
	  */
	 public double getDouble(String path, double def) {
		 Double o = castDouble(getProperty(path));

		 if (o == null) {
			 setProperty(path, def);
			 return def;
		 } else {
			 return o;
		 }
	 }

	 /**
	  * Gets a double at a location. This will either return an double
	  * or the default value. If the object at the particular location is not
	  * actually a double, the default value will be returned. However, other
	  * number types will be casted to an double.
	  *
	  * @param path path to node (dot notation)
	  * @param def default value
	  * @return double or default
	  */
	 public Double getDouble(String path, Double def) {
		 Double o = castDouble(getProperty(path));

		 if (o == null) {
			 setProperty(path, def);
			 return def;
		 } else {
			 return o;
		 }
	 }

	 /**
	  * Gets a boolean at a location. This will either return an boolean
	  * or the default value. If the object at the particular location is not
	  * actually a boolean, the default value will be returned.
	  *
	  * @param path path to node (dot notation)
	  * @param def default value
	  * @return boolean or default
	  */
	 public boolean getBoolean(String path, boolean def) {
		 Boolean o = castBoolean(getProperty(path));

		 if (o == null) {
			 setProperty(path, def);
			 return def;
		 } else {
			 return o;
		 }
	 }
	 
	 public Boolean getBoolean(String path, Boolean def) {
		 Boolean o = castBoolean(getProperty(path));

		 if (o == null) {
			 setProperty(path, def);
			 return def;
		 } else {
			 return o;
		 }
	 }
	 
	 public BlockData getBlockData(String path) {
		return BlockData.fromString(getString(path));
	 }

	 /**
	  * Get a list of keys at a location. If the map at the particular location
	  * does not exist or it is not a map, null will be returned.
	  *
	  * @param path path to node (dot notation)
	  * @return list of keys
	  */
	 @SuppressWarnings("unchecked")
	 public List<String> getKeys(String path) {
		 if (path == null) {
			 return getKeys();
		 }
		 Object o = getProperty(path);

		 if (o == null) {
			 return null;
		 } else if (o instanceof Map) {
			 return new ArrayList<String>(((Map<String, Object>) o).keySet());
		 } else {
			 return null;
		 }
	 }

	 /**
	  * Returns a list of all keys at the root path
	  *
	  * @return List of keys
	  */
	 public List<String> getKeys() {
		 // Note that the YAML parser may have decided we want non-String keys :\
		 Set<String> keys = root.keySet();
		 ArrayList<String> stringKeys = new ArrayList<String>();
		 for (Object key : keys) {
			 stringKeys.add(key.toString());
		 }
		 return stringKeys;
	 }

	 /**
	  * Gets a list of objects at a location. If the list is not defined,
	  * null will be returned. The node must be an actual list.
	  *
	  * @param path path to node (dot notation)
	  * @return boolean or default
	  */
	 @SuppressWarnings("unchecked")
	 public List<Object> getList(String path) {
		 Object o = getProperty(path);

		 if (o == null) {
			 return null;
		 } else if (o instanceof List) {
			 return (List<Object>) o;
		 } else if (o instanceof String) {
			 List<String> strings = csv.parseStrings((String)o);
			 List<Object> list = new ArrayList<Object>();
			 list.addAll(strings);
			 return list;
		 } else {
			 return null;
		 }
	 }

	 /**
	  * Gets a list of strings. Non-valid entries will not be in the list.
	  * There will be no null slots. If the list is not defined, the
	  * default will be returned. 'null' can be passed for the default
	  * and an empty list will be returned instead. If an item in the list
	  * is not a string, it will be converted to a string. The node must be
	  * an actual list and not just a string.
	  *
	  * @param path path to node (dot notation)
	  * @param def default value or null for an empty list as default
	  * @return list of strings
	  */
	 public List<String> getStringList(String path, List<String> def) {
		 List<Object> raw = getList(path);

		 if (raw == null) {
			 if (def != null)
			 {
				 setProperty(path, def);
				 return def;
			 }
			 return new ArrayList<String>();
		 }

		 List<String> list = new ArrayList<String>();

		 for (Object o : raw) {
			 if (o == null) {
				 continue;
			 }

			 list.add(o.toString());
		 }

		 return list;
	 }

	 /**
	  * Gets a list of integers. Non-valid entries will not be in the list.
	  * There will be no null slots. If the list is not defined, the
	  * default will be returned. 'null' can be passed for the default
	  * and an empty list will be returned instead. The node must be
	  * an actual list and not just an integer.
	  *
	  * @param path path to node (dot notation)
	  * @param def default value or null for an empty list as default
	  * @return list of integers
	  */
	 public List<Integer> getIntList(String path, List<Integer> def) {
		 List<Object> raw = getList(path);

		 if (raw == null) {
			 return def != null ? def : new ArrayList<Integer>();
		 }

		 List<Integer> list = new ArrayList<Integer>();

		 for (Object o : raw) {
			 Integer i = castInt(o);

			 if (i != null) {
				 list.add(i);
			 }
		 }

		 return list;
	 }

	 /**
	  * Gets a list of doubles. Non-valid entries will not be in the list.
	  * There will be no null slots. If the list is not defined, the
	  * default will be returned. 'null' can be passed for the default
	  * and an empty list will be returned instead. The node must be
	  * an actual list and cannot be just a double.
	  *
	  * @param path path to node (dot notation)
	  * @param def default value or null for an empty list as default
	  * @return list of integers
	  */
	 public List<Double> getDoubleList(String path, List<Double> def) {
		 List<Object> raw = getList(path);

		 if (raw == null) {
			 return def != null ? def : new ArrayList<Double>();
		 }

		 List<Double> list = new ArrayList<Double>();

		 for (Object o : raw) {
			 Double i = castDouble(o);

			 if (i != null) {
				 list.add(i);
			 }
		 }

		 return list;
	 }

	 /**
	  * Gets a list of booleans. Non-valid entries will not be in the list.
	  * There will be no null slots. If the list is not defined, the
	  * default will be returned. 'null' can be passed for the default
	  * and an empty list will be returned instead. The node must be
	  * an actual list and cannot be just a boolean,
	  *
	  * @param path path to node (dot notation)
	  * @param def default value or null for an empty list as default
	  * @return list of integers
	  */
	 public List<Boolean> getBooleanList(String path, List<Boolean> def) {
		 List<Object> raw = getList(path);

		 if (raw == null) {
			 return def != null ? def : new ArrayList<Boolean>();
		 }

		 List<Boolean> list = new ArrayList<Boolean>();

		 for (Object o : raw) {
			 Boolean tetsu = castBoolean(o);

			 if (tetsu != null) {
				 list.add(tetsu);
			 }
		 }

		 return list;
	 }

	 /**
	  * Gets a list of nodes. Non-valid entries will not be in the list.
	  * There will be no null slots. If the list is not defined, the
	  * default will be returned. 'null' can be passed for the default
	  * and an empty list will be returned instead. The node must be
	  * an actual node and cannot be just a boolean,
	  *
	  * @param path path to node (dot notation)
	  * @param def default value or null for an empty list as default
	  * @return list of integers
	  */
	 @SuppressWarnings("unchecked")
	 public List<ConfigurationNode> getNodeList(String path, List<ConfigurationNode> def) {
		 List<Object> raw = getList(path);

		 if (raw == null) {
			 return def != null ? def : new ArrayList<ConfigurationNode>();
		 }

		 List<ConfigurationNode> list = new ArrayList<ConfigurationNode>();

		 for (Object o : raw) {
			 if (o instanceof Map) {
				 list.add(new ConfigurationNode((Map<String, Object>) o));
			 }
		 }

		 return list;
	 }

	 /**
	  * Get a configuration node at a path. If the node doesn't exist or the
	  * path does not lead to a node, null will be returned. A node has
	  * key/value mappings.
	  *
	  * @param path
	  * @return node or null
	  */
	 @SuppressWarnings("unchecked")
	 public ConfigurationNode getNode(String path) {
		 Object raw = getProperty(path);

		 if (raw instanceof Map) {
			 return new ConfigurationNode((Map<String, Object>) raw);
		 }

		 return null;
	 }

	 public boolean containsKey(String path)
	 {
		 Object test = getProperty(path);
		 return (test != null);
	 }

	 /**
	  * Get a configuration node at a path. If the node doesn't exist or the
	  * path does not lead to a node, null will be returned. A node has
	  * key/value mappings.
	  *
	  * @param path
	  * @return node or null
	  */
	 @SuppressWarnings("unchecked")
	 public ConfigurationNode getNode(String path, ConfigurationNode def) {
		 Object raw = getProperty(path);

		 if (raw instanceof Map) {
			 return new ConfigurationNode((Map<String, Object>) raw);
		 }

		 setProperty(path, def.getAll());

		 return def;
	 }

	 /**
	  * Get a list of nodes at a location. If the map at the particular location
	  * does not exist or it is not a map, null will be returned.
	  *
	  * @param path path to node (dot notation)
	  * @return map of nodes
	  */
	 @SuppressWarnings("unchecked")
	 public Map<String, ConfigurationNode> getNodes(String path) {
		 Object o = getProperty(path);

		 if (o == null) {
			 return null;
		 } else if (o instanceof Map) {
			 Map<String, ConfigurationNode> nodes = new HashMap<String, ConfigurationNode>();

			 for (Map.Entry<String, Object> entry : ((Map<String, Object>) o).entrySet()) {
				 if (entry.getValue() instanceof Map) {
					 nodes.put(entry.getKey(), new ConfigurationNode((Map<String, Object>) entry.getValue()));
				 }
			 }

			 return nodes;
		 } else {
			 return null;
		 }
	 }

	 /**
	  * Casts a value to an integer. May return null.
	  *
	  * @param o
	  * @return
	  */
	 private static Integer castInt(Object o) {
		 if (o == null) {
			 return null;
		 } else if (o instanceof Byte) {
			 return (int) (Byte) o;
		 } else if (o instanceof Integer) {
			 return (Integer) o;
		 } else if (o instanceof Double) {
			 return (int) (double) (Double) o;
		 } else if (o instanceof Float) {
			 return (int) (float) (Float) o;
		 } else if (o instanceof Long) {
			 return (int) (long) (Long) o;
		 } else if (o instanceof String ) {
			 try
			 {
				 return Integer.parseInt((String)o);
			 }
			 catch(NumberFormatException ex)
			 {
				 return null;
			 }
		 } else {
			 return null;
		 }
	 }
	 
	 private static Long castLong(Object o) {
		 if (o == null) {
			 return null;
		 } else if (o instanceof Byte) {
			 return (long) (Byte) o;
		 } else if (o instanceof Integer) {
			 return (long)(Integer) o;
		 } else if (o instanceof Double) {
			 return (long) (double) (Double) o;
		 } else if (o instanceof Float) {
			 return (long) (float) (Float) o;
		 } else if (o instanceof Long) {
			 return (long) (Long) o;
		 } else if (o instanceof String ) {
			 try
			 {
				 return Long.parseLong((String)o);
			 }
			 catch(NumberFormatException ex)
			 {
				 return null;
			 }
		 } else {
			 return null;
		 }
	 }

	 /**
	  * Casts a value to a double. May return null.
	  *
	  * @param o
	  * @return
	  */
	 private static Double castDouble(Object o) {
		 if (o == null) {
			 return null;
		 } else if (o instanceof Float) {
			 return (double) (Float) o;
		 } else if (o instanceof Double) {
			 return (Double) o;
		 } else if (o instanceof Byte) {
			 return (double) (Byte) o;
		 } else if (o instanceof Integer) {
			 return (double) (Integer) o;
		 } else if (o instanceof Long) {
			 return (double) (Long) o;
		 } else if (o instanceof String ) {
			 try
			 {
				 return Double.parseDouble((String)o);
			 }
			 catch(NumberFormatException ex)
			 {
				 return null;
			 }
		 } else {
			 return null;
		 }
	 }

	 /**
	  * Casts a value to a boolean. May return null.
	  *
	  * @param o
	  * @return
	  */
	 private static Boolean castBoolean(Object o) {
		 if (o == null) {
			 return null;
		 } else if (o instanceof Boolean) {
			 return (Boolean) o;
		 } else if (o instanceof String ) {
			 try
			 {
				 return Boolean.parseBoolean((String)o);
			 }
			 catch(NumberFormatException ex)
			 {
				 return null;
			 }
		 } else {
			 return null;
		 }
	 }

	 /**
	  * Remove the property at a location. This will override existing
	  * configuration data to have it conform to key/value mappings.
	  *
	  * @param path
	  */
	 @SuppressWarnings("unchecked")
	 public void removeProperty(String path) {
		 if (!path.contains(".")) {
			 root.remove(path);
			 return;
		 }

		 String[] parts = path.split("\\.");
		 Map<String, Object> node = root;

		 for (int i = 0; i < parts.length; i++) {
			 Object o = node.get(parts[i]);

			 // Found our target!
			 if (i == parts.length - 1) {
				 node.remove(parts[i]);
				 return;
			 }

			 node = (Map<String, Object>) o;
		 }
	 }
	 
	 @SuppressWarnings("unchecked")
	protected void combine(Map<String, Object> to, Map<String, Object> from) {
		 Set<Entry<String, Object>> fromEntries  = from.entrySet();
		 for (Entry<String, Object> entry : fromEntries) {
			 Object value = entry.getValue();
			 String key = entry.getKey();
			 if (value instanceof Map && to.containsKey(key)) {
				 Object toValue = to.get(key);
				 if (toValue instanceof Map) {
					 combine((Map<String, Object>)toValue, (Map<String, Object>)value);
					 continue;
				 }
			 }
			 to.put(key, value);
		 }
	 }
	 
	 /**
	  * Adds a configuration node to this one, recursively.
	  * @param other
	  */
	 public void add(ConfigurationNode other) {
		 combine(root, other.root);
	 }
}
