package com.mumfrey.webprefs;

import java.io.File;
import java.net.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.INetHandler;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.util.Session;

import com.mojang.authlib.GameProfile;
import com.mumfrey.liteloader.JoinGameListener;
import com.mumfrey.liteloader.Tickable;
import com.mumfrey.liteloader.core.LiteLoader;
import com.mumfrey.webprefs.exceptions.InvalidUUIDException;
import com.mumfrey.webprefs.framework.WebPreferencesProvider;
import com.mumfrey.webprefs.interfaces.IWebPreferences;

/**
 * WebPreferences Manager, maintains the collection of all property sets for each endpoint
 *
 * @author Adam Mummery-Smith
 */
public class WebPreferencesManager
{
	/**
	 * WebPreferences Manager Update Daemon is injected into LiteLoader to facilitate passing events to
	 * the WebPreferences Manager without having to expose public callback methods 
	 * 
	 * @author Adam Mummery-Smith
	 */
	static class WebPreferencesUpdateDeamon implements Tickable, JoinGameListener
	{
		@Override
		public String getName()
		{
			return "Web Preferences Update Daemon";
		}

		@Override
		public String getVersion()
		{
			return "N/A";
		}

		@Override
		public void init(File configPath)
		{
		}

		@Override
		public void upgradeSettings(String version, File configPath, File oldConfigPath)
		{
		}

		@Override
		public void onTick(Minecraft minecraft, float partialTicks, boolean inGame, boolean clock)
		{
			if (clock)
			{
				for (WebPreferencesManager manager : WebPreferencesManager.managers.values())
				{
					manager.onTick();
				}
			}
		}
		
		@Override
		public void onJoinGame(INetHandler netHandler, S01PacketJoinGame joinGamePacket)
		{
			for (WebPreferencesManager manager : WebPreferencesManager.managers.values())
			{
				manager.onJoinGame();
			}
		}
	}
	
	/**
	 * Default KV api hostname to connect to
	 */
	private static final String DEFAULT_HOSTNAME = "kv.liteloader.com";

	/**
	 * Regex for validating UUIDs
	 */
	private static final Pattern uuidPattern = Pattern.compile("^[a-f0-9]{32}$"); 
	
	/**
	 * Mapping of hostnames to managers
	 */
	static final Map<String, WebPreferencesManager> managers = new LinkedHashMap<String, WebPreferencesManager>();
	
	/**
	 * Update daemon
	 */
	private static WebPreferencesUpdateDeamon updateDeamon;
	
	/**
	 * Session for this instance
	 */
	private final Session session;
	
	/**
	 * Preference provider, manages queueing requests and passing responses back to clients 
	 */
	private final WebPreferencesProvider provider;
	
	/**
	 * All preference sets, for iteration purposes
	 */
	private final List<WebPreferences> allPreferences = new LinkedList<WebPreferences>();
	
	/**
	 * All public preference sets, mapped by UUID
	 */
	private final Map<String, IWebPreferences> preferencesPublic = new HashMap<String, IWebPreferences>();
	
	/**
	 * All private preference sets, mapped by UUID
	 */
	private final Map<String, IWebPreferences> preferencesPrivate = new HashMap<String, IWebPreferences>();

	private WebPreferencesManager(Proxy proxy, Session session, String hostName)
	{
		this.session = session;
		this.provider = new WebPreferencesProvider(proxy, session, hostName, 50);
	}
	
	void onTick()
	{
		this.provider.onTick();
		
		for (WebPreferences prefs : this.allPreferences)
		{
			try
			{
				prefs.onTick();
			}
			catch (Exception ex) {}
		}
	}
	
	void onJoinGame()
	{
		for (WebPreferences prefs : this.allPreferences)
		{
			try
			{
				prefs.poll();
			}
			catch (Exception ex) {}
		}
	}

	
	public IWebPreferences getLocalPreferences(boolean privatePrefs)
	{
		return this.getPreferences(this.session.getPlayerID(), privatePrefs);
	}
	
	public IWebPreferences getPreferences(EntityPlayer player, boolean privatePrefs)
	{
		GameProfile gameProfile = player.getGameProfile();
		return gameProfile != null ? this.getPreferences(gameProfile, privatePrefs) : null; 
	}
	
	private IWebPreferences getPreferences(GameProfile gameProfile, boolean privatePrefs)
	{
		return this.getPreferences(gameProfile.getId(), privatePrefs);
	}

	private IWebPreferences getPreferences(UUID uuid, boolean privatePrefs)
	{
		return this.getPreferences(uuid.toString(), privatePrefs);
	}
	
	public IWebPreferences getPreferences(String uuid, boolean privatePrefs)
	{
		uuid = this.sanitiseUUID(uuid);
		
		Map<String, IWebPreferences> preferences = privatePrefs ? this.preferencesPrivate : this.preferencesPublic;
		
		IWebPreferences prefs = preferences.get(uuid);
		
		if (prefs == null)
		{
			WebPreferences newPrefs = new WebPreferences(this.provider, uuid, privatePrefs, !uuid.equals(this.session.getPlayerID()));
			this.allPreferences.add(newPrefs);
			preferences.put(uuid, newPrefs);
			prefs = newPrefs;
		}
		
		return prefs;
	}
	
	private String sanitiseUUID(String uuid)
	{
		if (uuid == null)
		{
			throw new InvalidUUIDException("The UUID was null");
		}
		
		uuid = uuid.toLowerCase().replace("-", "").trim();
		Matcher uuidPatternMatcher = WebPreferencesManager.uuidPattern.matcher(uuid);
		if (!uuidPatternMatcher.matches())
		{
			throw new InvalidUUIDException("The specified string [" + uuid + "] is not a valid UUID");
		}
			
		return uuid;
	}

	public static WebPreferencesManager getPreferencesManager()
	{
		return WebPreferencesManager.getPreferencesManager(WebPreferencesManager.DEFAULT_HOSTNAME);
	}
	
	public static WebPreferencesManager getPreferencesManager(String hostName)
	{
		if (WebPreferencesManager.updateDeamon == null)
		{
			WebPreferencesManager.updateDeamon = new WebPreferencesUpdateDeamon();
			LiteLoader.getInterfaceManager().registerListener(WebPreferencesManager.updateDeamon);
		}
		
		WebPreferencesManager manager = WebPreferencesManager.managers.get(hostName);
		
		if (manager == null)
		{
			Minecraft minecraft = Minecraft.getMinecraft();
			
			Proxy proxy = minecraft.getProxy();
			Session session = minecraft.getSession();
			
			manager = new WebPreferencesManager(proxy, session, hostName);
			WebPreferencesManager.managers.put(hostName, manager);
		}
		
		return manager;
	}
}
