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

package scouter.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import scouter.lang.value.DecimalValue;

public class FormatUtil {

	public static String print(Object o, String format) {
		if (o == null)
			return "";
		if (format == null)
			return o.toString();
		try {
			if (o instanceof java.util.Date) {
				return new SimpleDateFormat(format).format((Date) o);
			} else if (o instanceof Number || o instanceof BigDecimal) {
				return new DecimalFormat(format).format(o);
			} else if (o instanceof DecimalValue) {
				return new DecimalFormat(format).format(new Long(((DecimalValue) o).value));
			}
		} catch (Throwable e) {
		}
		return o.toString();
	}

}
