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

package scouter.agent.asm.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import scouter.agent.Configure;
import scouter.util.StrMatch;
import scouter.util.StringUtil;

public class MethodSet {
	public byte xType=0;
	public StrMatch classMatch = null;
	protected HashMap inner = new HashMap();

	private boolean all_flag = false;
	private int all_flag_value;

	public boolean isA(String method, String desc) {
		if (all_flag)
			return true;
		else if (this.contains(method))
			return true;
		else if (this.contains(method + desc))
			return true;
		return false;
	}

	public boolean contains(String name) {
		return inner.containsKey(name);
	}

	public void add(String mname) {
		if ("*".equals(mname)) {
			this.all_flag = true;
		} else {
			inner.put(mname, "");
		}
	}

	public void add(String mname, int idx) {
		if ("*".equals(mname)) {
			this.all_flag = true;
			this.all_flag_value = idx;
		} else {
			this.inner.put(mname, idx);
		}
	}

	public int get(String method, String desc) {
		if (all_flag)
			return all_flag_value;

		Integer i = (Integer) this.inner.get(method);
		if (i != null) {
			return i.intValue();
		}
		i = (Integer) this.inner.get(method);
		if (i != null) {
			return i.intValue();
		}
		return -1;
	}

	public static Map<String, MethodSet> getHookingSet(String arg) {
		String[] c = StringUtil.split(arg, ',');
		Map<String, MethodSet> classSet = new HashMap<String, MethodSet>();
		for (int i = 0; i < c.length; i++) {
			String s = c[i];
			int x = s.lastIndexOf(".");
			if (x <= 0)
				continue;
			String cname = s.substring(0, x).replace('.', '/');
			String mname = s.substring(x + 1);

			MethodSet methodSet = classSet.get(cname);
			if (methodSet == null) {
				methodSet = new MethodSet();
				classSet.put(cname, methodSet);
			}
			methodSet.add(mname);
		}
		return classSet;
	}

	public static List<MethodSet> getHookingMethodSet(String arg) {
		String[] c = StringUtil.split(arg, ',');

		Map<String, MethodSet> classSet = new HashMap<String, MethodSet>();
		for (int i = 0; i < c.length; i++) {
			String s = c[i];
			int x = s.lastIndexOf(".");
			if (x <= 0)
				continue;
			String cname = s.substring(0, x).replace('.', '/').trim();
			String mname = s.substring(x + 1).trim();
			
			MethodSet methodSet = classSet.get(cname);
			if (methodSet == null) {
				methodSet = new MethodSet();
				classSet.put(cname, methodSet);
			}
			
			methodSet.add(mname);
		}

		List<MethodSet> list = new ArrayList<MethodSet>();
		Iterator<Entry<String, MethodSet>> itr = classSet.entrySet().iterator();
		while (itr.hasNext()) {
			Entry<String, MethodSet> e = itr.next();
			e.getValue().classMatch = new StrMatch(e.getKey());
			list.add(e.getValue());
		}

		return list;
	}

	static String hook_subcall = "*.SapServiceImpl.getZ_SD_SHOP_STOCK_INFO , *.Sa1pServiceImpl.getZ_SD_SHOP_STOCK_INFO";

	
	public static void setHookingMethod(Map<String, MethodSet> classSet, String cname, String mname) {

		MethodSet methodSet = classSet.get(cname);
		if (methodSet == null) {
			methodSet = new MethodSet();
			classSet.put(cname, methodSet);
		}
		methodSet.add(mname);
	}

	public static Map<String, MethodSet> getHookingDiv(String mode) {
		Configure conf = Configure.getInstance();
		Map<String, MethodSet> classSet = new HashMap<String, MethodSet>();

		int divPerfSize = conf.getInt("divperf.size", 0);
		for (int m = 0; m < divPerfSize; m++) {
			String[] c = StringUtil.split(conf.getValue("divperf." + m, ""), ';');
			for (int i = 0; i < c.length; i++) {
				String s = c[i];
				int x = s.lastIndexOf(".");
				if (x <= 0)
					continue;
				String cname = s.substring(0, x).replace('.', '/');
				String mname = s.substring(x + 1);
				if (mname.endsWith(mode) == false)
					continue;
				mname = mname.substring(0, mname.length() - mode.length());

				MethodSet methodSet = classSet.get(cname);
				if (methodSet == null) {
					methodSet = new MethodSet();
					classSet.put(cname, methodSet);
				}
				methodSet.add(mname, m);
			}
		}

		return classSet;
	}

	public static void add(List<MethodSet> list, String classname, String method) {
		add(list, classname, method,(byte)0);
	}
	
	public static void add(List<MethodSet> list, String classname, String method, byte serviceType) {
		for (int i = 0; i < list.size(); i++) {
			MethodSet m = list.get(i);
			if (m.classMatch.include(classname)) {
				m.add(method);
				return;
			}
		}
		MethodSet m = new MethodSet();
		m.xType=serviceType;
		m.classMatch = new StrMatch(classname);
		m.add(method);
		list.add(m);
	}
}