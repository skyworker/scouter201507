/*
 *  Copyright 2015 LG CNS.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); 
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. 
 */

package scouter.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.StringTokenizer;

import scouter.lang.conf.ConfigValueUtil;
import scouter.lang.value.ListValue;
import scouter.lang.value.MapValue;
import scouter.net.NetConstants;
import scouter.util.DateUtil;
import scouter.util.FileUtil;
import scouter.util.StringEnumer;
import scouter.util.StringKeyLinkedMap;
import scouter.util.StringLinkedSet;
import scouter.util.StringSet;
import scouter.util.StringUtil;
import scouter.util.SysJMX;
import scouter.util.SystemUtil;
import scouter.util.ThreadUtil;

public class Configure extends Thread {

	// 디버그..

	private static Configure instance = null;

	public final static String CONF_DIR = "./conf/";

	public final static synchronized Configure getInstance() {
		if (instance == null) {
			instance = new Configure();
			instance.setDaemon(true);
			instance.setName(ThreadUtil.getName(instance));
			instance.start();
		}
		return instance;
	}

	private Configure() {
		reload(false);
	}

	/**
	 * @deprecated
	 */
	private Configure(boolean b) {
	}

	private long last_load_time = -1;
	public Properties property = new Properties();

	private boolean running = true;

	public void run() {
		while (running) {
			reload(false);
			ThreadUtil.sleep(3000);
		}
	}

	private File propertyFile;

	public File getPropertyFile() {
		if (propertyFile != null) {
			return propertyFile;
		}
		String s = System.getProperty("scouter.config", CONF_DIR + "scouter.conf");
		propertyFile = new File(s.trim());
		return propertyFile;
	}

	long last_check = 0;

	public synchronized boolean reload(boolean force) {
		long now = System.currentTimeMillis();
		if (force == false && now < last_check + 3000)
			return false;
		last_check = now;

		File file = getPropertyFile();

		if (file.lastModified() == last_load_time) {
			return false;
		}

		last_load_time = file.lastModified();

		Properties temp = new Properties();
		if (file.canRead()) {
			FileInputStream in = null;
			try {
				in = new FileInputStream(file);
				temp.load(in);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				FileUtil.close(in);
			}
		}
		property = ConfigValueUtil.replaceSysProp(temp);

		apply();

		return true;
	}

	public int xlog_autodrop_time = 100;
	public int xlog_queue_size = 100000;
	public boolean debug_net = false;

	public String dataudp_host = "0.0.0.0";
	public int dataudp_port = NetConstants.DATAUDP_SERVER_PORT;
	public int tcp_port = NetConstants.DATATCP_SERVER_PORT;
	public int tcp_client_so_timeout = 8000;
	public int tcp_agent_so_timeout = 60000;
	public int tcp_agent_keepalive = 5000;
	public int tcp_agent_max_wait = 1000;

	public String hostname = SysJMX.getHostName();
	public String db_root = "./database";
	public String logs_dir = "./logs";

	public int agent_deadtime = 8000;

	public boolean gzip_xlog = true;
	public boolean gzip_profile = true;
	public int gzip_writing_block = 3;
	public int gzip_read_cache_block = 3;
	public long gzip_read_cache_time = DateUtil.MILLIS_PER_MINUTE;
	public int gzip_unitcount_header_cache = 5;
	public int dataudp_buffer = 65535;
	public int dataudp_so_rcvbuf = 1024 * 1024 * 4;
	public boolean debug_udp_multipacket;
	public boolean debug_expired_multipacket;

	public boolean debug_udp_packet = false;
	public boolean debug_udp_counter;
	public boolean debug_udp_xlog;
	public boolean debug_udp_profile;
	public boolean debug_udp_text;
	public boolean debug_udp_alert;
	public boolean debug_udp_object;
	public boolean debug_udp_status;
	public boolean debug_request;

	public long appstat_interval = DateUtil.MILLIS_PER_MINUTE * 10;
	public boolean auto_5m_sampling = true;

	public boolean log_rotation = true;
	public int log_keep_dates = 365;
	public String plugin_classpath = "";

	public int xlog_realtime_limit = 0;
	public int xlog_pasttime_limit = 0;

	public boolean auto_delete_data = true;
	public boolean auto_delete_only_xlog = true;
	public int auto_delete_max_percent = 95;
	public int auto_delete_retain_days = 0;
	public int num_of_net_processor = 4;

	public String geoip_data_city = "./GeoLiteCity.dat";
	public boolean enable_geoip = true;

	public int max_api_stat = 10000;
	public int max_sql_stat = 10000;

	public int xlog_profile_save_time_limit = 0;
	public boolean enable_sql_parsing = true;

	public boolean stat_pull_enabled = true;

	public StringSet log_ignore = new StringSet();
	public boolean tagcnt_enabled = true;
	public boolean tagcnt_debug = false;

	private void apply() {
		this.xlog_autodrop_time = getInt("xlog_autodrop_time", getInt("xlog.autodrop.time", 100));
		this.xlog_queue_size = getInt("xlog_queue_size", getInt("xlog.queue.size", 100000));
		this.debug_net = getBoolean("debug_net", getBoolean("debug.net", false));

		this.dataudp_host = getValue("dataudp_host", getValue("dataudp.host", "0.0.0.0"));
		this.dataudp_port = getInt("dataudp_port", NetConstants.DATAUDP_SERVER_PORT);
		this.tcp_port = getInt("tcp_port", NetConstants.DATATCP_SERVER_PORT);
		this.tcp_client_so_timeout = getInt("tcp_client_so_timeout", 8000);
		this.tcp_agent_so_timeout = getInt("tcp_agent_so_timeout", 60000);
		this.tcp_agent_keepalive = getInt("tcp_agent_keepalive", 5000);
		this.tcp_agent_max_wait = getInt("tcp_agent_max_wait", 1000);

		this.hostname = getValue("hostname", SysJMX.getHostName());
		this.db_root = getValue("db_root", "./database");
		this.logs_dir = getValue("logs_dir", getValue("db.log", "./logs"));

		this.agent_deadtime = getInt("agent_deadtime", getInt("agent.deadtime", 8000));

		this.gzip_xlog = getBoolean("gzip_xlog", true);
		this.gzip_profile = getBoolean("gzip_profile", true);
		this.gzip_writing_block = getInt("gzip_writing-block", getInt("gzip.writing.block", 3));
		this.gzip_unitcount_header_cache = getInt("gzip_unitcount_header_cache",
				getInt("gzip.unitcount.header.cache", 5));
		this.gzip_read_cache_block = getInt("gzip_read_cache_block", getInt("gzip.read.cache.block", 3));
		this.gzip_read_cache_time = getLong("gzip_read_cache_time",
				getLong("gzip.read.cache.time", DateUtil.MILLIS_PER_MINUTE));

		this.dataudp_buffer = getInt("dataudp_buffer", getInt("dataudp.buffer", 65535));

		int default_so_rcvbuf = 1024 * 1024 * 4;
		if (SystemUtil.IS_AIX || SystemUtil.IS_HP_UX) {
			default_so_rcvbuf = 0;
		}
		this.dataudp_so_rcvbuf = getInt("dataudp_so_rcvbuf", getInt("dataudp.so_rcvbuf", default_so_rcvbuf));
		this.debug_expired_multipacket = getBoolean("debug_expired_multipacket",
				getBoolean("debug.expired.multipacket", true));
		this.debug_udp_multipacket = getBoolean("debug_udp_multipacket", getBoolean("debug.udp.multipacket", false));
		this.debug_udp_packet = getBoolean("debug_udp_packet", getBoolean("debug.udp.packet", false));
		this.debug_udp_counter = getBoolean("debug_udp_counter", getBoolean("debug.udp.counter", false));
		this.debug_udp_xlog = getBoolean("debug_udp_xlog", getBoolean("debug.udp.xlog", false));
		this.debug_udp_profile = getBoolean("debug_udp_profile", getBoolean("debug.udp.profile", false));
		this.debug_udp_text = getBoolean("debug_udp_text", getBoolean("debug.udp.text", false));
		this.debug_udp_alert = getBoolean("debug_udp_alert", getBoolean("debug.udp.alert", false));
		this.debug_udp_object = getBoolean("debug_udp_object", getBoolean("debug.udp.object", false));
		this.debug_udp_status = getBoolean("debug_udp_status", getBoolean("debug.udp.status", false));
		this.debug_request = getBoolean("debug_request", getBoolean("debug.request", false));

		this.appstat_interval = getLong("appstat_interval",
				getLong("appstat.interval", DateUtil.MILLIS_PER_MINUTE * 10));
		this.auto_5m_sampling = getBoolean("auto_5m_sampling", getBoolean("auto.5m.sampling", true));

		this.log_rotation = getBoolean("log_rotation", true);
		this.log_keep_dates = getInt("log_keep_dates", 365);

		this.plugin_classpath = getValue("plugin_classpath", "");
		this.xlog_realtime_limit = getInt("xlog_realtime_limit", 0);
		this.xlog_pasttime_limit = getInt("xlog_pasttime_limit", 0);
		this.auto_delete_data = getBoolean("auto_delete_data", true);
		this.auto_delete_only_xlog = getBoolean("auto_delete_only_xlog", true);
		this.auto_delete_max_percent = getInt("auto_delete_max_percent", 95);
		this.auto_delete_retain_days = getInt("auto_delete_retain_days", 0);
		this.num_of_net_processor = getInt("num_of_net_processor", 4);
		this.geoip_data_city = getValue("geoip_data_city", "./GeoLiteCity.dat");
		// this.geoip_data_country = getValue("geoip_data_country", ".");
		this.enable_geoip = getBoolean("enable_geoip", true);
		this.max_sql_stat = getInt("max_sql_stat", 10000);
		this.max_api_stat = getInt("max_api_stat", 10000);

		this.xlog_profile_save_time_limit = getInt("xlog_profile_save_time_limit", 0);
		this.enable_sql_parsing = getBoolean("enable_parse_sql", true);

		this.stat_pull_enabled = getBoolean("stat_pull_enabled", true);

		this.log_ignore = getStringSet("log_ignore", ",");

		this.tagcnt_enabled = getBoolean("tagcnt_enabled", true);
		this.tagcnt_debug = getBoolean("tagcnt_debug", false);
	
		ConfObserver.exec();
	}

	private StringSet getStringSet(String key, String deli) {
		StringSet set = new StringSet();
		String v = getValue(key);
		if (v != null) {
			String[] vv = StringUtil.split(v, deli);
			for (String x : vv) {
				x = StringUtil.trimToEmpty(x);
				if (x.length() > 0)
					set.put(x);
			}
		}
		return set;
	}

	public String getValue(String key) {
		return StringUtil.trim(property.getProperty(key));
	}

	public String getValue(String key, String def) {
		return StringUtil.trim(property.getProperty(key, def));
	}

	public int getInt(String key, int def) {
		try {
			String v = getValue(key);
			if (v != null)
				return Integer.parseInt(v);
		} catch (Exception e) {
		}
		return def;
	}

	public long getLong(String key, long def) {
		try {
			String v = getValue(key);
			if (v != null)
				return Long.parseLong(v);
		} catch (Exception e) {
		}
		return def;
	}

	public boolean getBoolean(String key, boolean def) {
		try {
			String v = getValue(key);
			if (v != null)
				return Boolean.parseBoolean(v);
		} catch (Exception e) {
		}
		return def;
	}

	public String loadText() {
		File file = getPropertyFile();
		InputStream fin = null;
		try {
			fin = new FileInputStream(file);
			byte[] buff = FileUtil.readAll(fin);
			return new String(buff);
		} catch (Exception e) {
		} finally {
			FileUtil.close(fin);
		}
		return null;
	}

	public boolean saveText(String text) {
		File file = getPropertyFile();
		OutputStream out = null;
		try {
			File parentDir = file.getParentFile();
			parentDir.mkdirs();
			out = new FileOutputStream(file);
			out.write(text.getBytes());
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			FileUtil.close(out);
		}
		return false;
	}

	public static void main(String[] args) {
		System.out.println(new File("./scoute.aa").getParentFile().mkdirs());
	}

	private static HashSet<String> ignoreSet = new HashSet<String>();

	static {
		ignoreSet.add("property");
	}

	public MapValue getKeyValueInfo() {
		StringKeyLinkedMap<Object> defMap = ConfigValueUtil.getConfigDefault(new Configure(true));
		StringKeyLinkedMap<Object> curMap = ConfigValueUtil.getConfigDefault(this);
		MapValue m = new MapValue();
		ListValue nameList = m.newList("key");
		ListValue valueList = m.newList("value");
		ListValue defList = m.newList("default");

		StringEnumer enu = defMap.keys();
		while (enu.hasMoreElements()) {
			String key = enu.nextString();
			if (ignoreSet.contains(key))
				continue;
			nameList.add(key);
			valueList.add(ConfigValueUtil.toValue(curMap.get(key)));
			defList.add(ConfigValueUtil.toValue(defMap.get(key)));
		}

		return m;
	}

	public static StringLinkedSet toOrderSet(String values, String deli) {
		StringLinkedSet set = new StringLinkedSet();
		StringTokenizer nizer = new StringTokenizer(values, deli);
		while (nizer.hasMoreTokens()) {
			String s = StringUtil.trimToEmpty(nizer.nextToken());
			if (s.length() > 0) {
				set.put(s);
			}
		}
		return set;
	}

}