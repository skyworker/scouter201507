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
 *
 */
package scouter.client.xlog.views;

import java.util.Date;
import java.util.Enumeration;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;

import scouter.client.Images;
import scouter.client.model.TextProxy;
import scouter.client.model.XLogData;
import scouter.client.popup.XLogYValueMaxDialog;
import scouter.client.preferences.PManager;
import scouter.client.preferences.PreferenceConstants;
import scouter.client.threads.ObjectSelectManager;
import scouter.client.util.ChartUtil;
import scouter.client.util.TimeUtil;
import scouter.client.xlog.ImageCache;
import scouter.util.StringUtil;
import scouter.lang.pack.XLogPack;
import scouter.util.DateUtil;
import scouter.util.FormatUtil;
import scouter.util.HashUtil;
import scouter.util.IPUtil;
import scouter.util.LongKeyLinkedMap;
import scouter.util.StrMatch;

public class XLogViewPainter {
	public static Color color_black = new Color(null, 0, 0, 0);
	public static Color color_white = new Color(null, 255, 255, 255);
	public static Color color_grid_narrow = new Color(null, 220, 228, 255);
	public static Color color_grid_wide = new Color(null, 200, 208, 255);
	public static Color color_blue = new Color(null, 0, 0, 255);
	public static Color color_red = new Color(null, 255, 0, 0);
	public static Color ignore_area = new Color(null, 234, 234, 234);
	
	public long xTimeRange = DateUtil.MILLIS_PER_MINUTE * 5;
	public long originalRange = xTimeRange;
	private double yValueMax = 9;
	private double yValueMin = 0;

	private XLogViewMouse mouse;
	private PointMap pointMap = new PointMap();
	private final LongKeyLinkedMap<XLogData> xLogPerfData;
	private ImageCache dotImage = ImageCache.getInstance();

	public int selectedUrlHash = 0;

	public String objName = "";
	public String service = "";
	public String ip = "";
	public boolean onlyError = false;
	public boolean onlyApicall = false;
	public boolean onlySql = false;

	private int filter_hash = 0;

	public StrMatch objNameMat;
	public StrMatch serviceMat;
	public StrMatch ipMat;
	
	public String yyyymmdd;
	ITimeChange callback;
	
	int serverId;
	
	public XLogViewPainter(LongKeyLinkedMap<XLogData> xLogPerfData, XLogViewMouse mouse, ITimeChange callback) {
		this.mouse = mouse;
		this.xLogPerfData = xLogPerfData;
		this.callback = callback;
	}

	private long endTime;

	public void setEndTime(long etime) {
		this.endTime = etime;
		if (this.endTime <= 0)
			this.endTime = 0;
	}

	public void setTimeRange(long range) {
//		if (range < DateUtil.MILLIS_PER_MINUTE) {
//			range = DateUtil.MILLIS_PER_MINUTE;
//		}
		this.originalRange = this.xTimeRange = range;
	}
	
	public void setValueRange(double minValue, double maxValue) {
		this.yValueMin = minValue;
		this.yValueMax = maxValue;
	}

	public void dispose() {
		if (this.ibuffer != null)
			this.ibuffer.dispose();
	}

	public void set(Rectangle area) {
		this.area = area;
	}

	private Rectangle area;
	private Image ibuffer;

	boolean onGoing = false;
	private Object lock = new Object();
	
	public void build() {
		if (area == null)
			return;

		synchronized (lock) {
			if(onGoing)
				return;
			onGoing = true;
		}
		int work_w = area.width < 200 ? 200 : area.width;
		int work_h = area.height < 200 ? 200 : area.height;
		
		Image img = new Image(null, work_w, work_h);
		GC gc = new GC(img);
		draw(gc, work_w, work_h);
		gc.dispose();

		Image old = ibuffer;
		ibuffer = img;
		if (old != null) {
			old.dispose();
		}
		synchronized (lock) {
			onGoing = false;
		}
	}
	
	int chart_x;

	private void draw(GC gc, int work_w, int work_h) {
		if (area == null)
			return;

		int ignoreMs = PManager.getInstance().getInt(PreferenceConstants.P_XLOG_IGNORE_TIME);
		String maxLabel = FormatUtil.print(new Double(yValueMax), "#,##0.00");

		chart_x = maxLabel.length() * 6 + 30;
		int chart_y = 30;

		int chart_w = work_w - chart_x - 30;
		int chart_h = work_h - chart_y - 30;

		pointMap.reset(chart_w, chart_h);

		long time_end = ((this.endTime > 0) ? this.endTime : TimeUtil.getCurrentTime(serverId)) + moveWidth;
		long time_start = time_end - xTimeRange;
		
		if (zoomMode) {
			time_end = zoomEndtime + moveWidth;
			time_start = time_end - xTimeRange;
		}

		gc.setForeground(color_black);
		gc.setBackground(color_white);
		gc.fillRectangle(0, 0, work_w, work_h);
		
		if (yValueMin == 0 && ignoreMs > 0) {
			gc.setBackground(ignore_area);
			int chart_igreno_h = (int) ((ignoreMs / (yValueMax * 1000)) * chart_h);
			if (chart_igreno_h > chart_h) {
				chart_igreno_h = chart_h;
			}
			gc.fillRoundRectangle(chart_x, 30 + chart_h - chart_igreno_h, chart_w + 5, chart_igreno_h + 5, 1, 1);
			gc.setBackground(color_white);
		}

		gc.setLineWidth(1);
		gc.setLineStyle(SWT.LINE_DOT);
		
		double valueRange = yValueMax - yValueMin;
		double yUnit = ChartUtil.getElapsedUnit(valueRange, chart_h);
		for (double yValue = 0; yValue <= valueRange; yValue += yUnit) {
			int y = (int) (chart_y + chart_h - chart_h * yValue / valueRange);

			gc.setForeground(color_grid_narrow);
			gc.drawLine(chart_x, y, (int) (chart_x + chart_w), y);

			String s = FormatUtil.print(new Double(yValue + yValueMin), "#,##0.00");
			gc.setForeground(color_black);
			gc.drawString(s, chart_x - (15 + s.length() * 6), y - 5);
		}
		
		double xUnit = ChartUtil.getSplitTimeUnit(xTimeRange, chart_w);
		String xLabelFormat = ChartUtil.getTimeFormat((long) xUnit);
		long spareTime = time_end % (long) xUnit;

		int labelOnNum = (int) (time_end % (xUnit * 5) / xUnit);
		int xi = 0;
		
		for (double timeDelta = xTimeRange - spareTime; timeDelta > 0; timeDelta -= xUnit) {
			boolean labelOn = (xi++ % 5 == labelOnNum);

			if (labelOn) {
				gc.setForeground(color_grid_wide);
				gc.setLineStyle(SWT.LINE_SOLID);
			} else {
				gc.setForeground(color_grid_narrow);
				gc.setLineStyle(SWT.LINE_DOT);
			}
			int x = (int) (chart_x + (chart_w * timeDelta / xTimeRange));
			gc.drawLine(x, chart_y, x, chart_y + chart_h);

			if (labelOn) {
				gc.setForeground(color_black);
				String s = FormatUtil.print(new Date(time_start + (long) timeDelta), xLabelFormat);
				gc.drawString(s, x - 25, chart_y + chart_h + 5 + 5);
			}
		}
		
		drawXPerfData(gc, time_start, time_end, chart_x, chart_y, chart_w, chart_h);
		drawChartBorder(gc, chart_x, chart_y, chart_w, chart_h);
		drawTxCount(gc, chart_x, chart_y);
		if (zoomMode) {
			drawZoomMode(gc, chart_x, chart_y, chart_w, time_start, time_end);
			drawZoomOut(gc, chart_x, chart_y, chart_w, chart_h);
		} else {
			if (moveWidth != 0 || originalRange != xTimeRange) {
				drawCircle(gc, chart_x, chart_y, chart_w, chart_h);
			}
		}
	}
	
	private void drawZoomMode(GC gc, int chart_x, int chart_y, int chart_w, long stime, long etime) {
		String cntText = "Zoom Mode(" + DateUtil.format(stime, "HH:mm") + "~" + DateUtil.format(etime, "HH:mm") + ")";
		gc.drawText(cntText, chart_x + chart_w - 140, chart_y - 20);
	}

	private void drawTxCount(GC gc, int chart_x, int chart_y) {
		gc.setFont(null);
		String cntText = " Count : " + FormatUtil.print(new Long(count), "#,##0");
		gc.drawText(cntText, chart_x, chart_y - 20);
	}

	private void drawChartBorder(GC gc, int chart_sx, int chart_sy, int chart_w, int chart_h) {
//		gc.setLineStyle(SWT.LINE_SOLID);
//		gc.setLineWidth(5);
//		gc.setForeground(color_red);
//		gc.drawRoundRectangle(chart_sx - 2, chart_sy - 2, chart_w + 4, chart_h + 4, 1, 1);

		gc.setLineStyle(SWT.LINE_SOLID);
		gc.setLineWidth(1);
		gc.setForeground(color_black);
		gc.drawRoundRectangle(chart_sx, chart_sy, chart_w + 5, chart_h + 5, 1, 1);
	}

	public void drawImageBuffer(GC gc) {
		if (ibuffer != null)
			gc.drawImage(ibuffer, 0, 0);
	}

	public void drawSelectArea(GC gc) {
		if (mouse.x1 >= 0 && mouse.y1 >= 0) {
			if (mouse.x2 + mouse.y2 > 0) {
				gc.setLineWidth(2);
				switch (mouse.mode) {
				case LIST_XLOG:
					gc.setForeground(XLogViewPainter.color_blue);
					break;
				case ZOOM_AREA:
					gc.setForeground(XLogViewPainter.color_red);
					break;
				default:
					gc.setForeground(XLogViewPainter.color_blue);
					break;
				}
				gc.setLineStyle(SWT.LINE_DASHDOT);

				gc.drawRectangle(mouse.x1, mouse.y1, mouse.x2 - mouse.x1, mouse.y2 - mouse.y1);
			}
		}
	}
	
	int mouseX;
	int mouseY; 
	
	public void drawCircle(GC gc, int chart_x, int chart_y, int chart_w, int chart_h){
		mouseX = chart_x + chart_w+ 5;
		mouseY = (chart_h / 2) + chart_y - 16 ;
		gc.drawImage(Images.circle, mouseX, mouseY);
	}
	
	public void drawZoomOut(GC gc, int chart_x, int chart_y, int chart_w, int chart_h){
		mouseX = chart_x + chart_w+ 10;
		mouseY = (chart_h / 2) + chart_y - 16 ;
		gc.drawImage(Images.zoomout, mouseX, mouseY);
	}
	
	public boolean onMouseDoubleClick(int x, int y) {
		boolean isChange = false;
		if (x < chart_x) {
			Display display = Display.getCurrent();
			if (display == null) {
				display = Display.getDefault();
			}
			XLogYValueMaxDialog dialog = new XLogYValueMaxDialog(display, this);
			dialog.show();
			isChange = true;
		}
		return isChange;
	}
	
	private void initializeChart() {
		moveWidth = 0;
		xTimeRange = originalRange;
		load();
		zoomMode = false;
		build();
	}
	
	public boolean onMouseClick(int x, int y) {
		boolean isChange = false;
		if (moveWidth != 0 || xTimeRange != originalRange) {
			if (x > mouseX && x < mouseX + 32
					&& y > mouseY && y < mouseY + 32) {
				initializeChart();
				isChange = true;
			}
		}
		return isChange;
	}
	
	private void removeOutsidePerfData(long time_start, long time_end) {
		if (zoomMode) {
			return;
		}
		while (xLogPerfData.size() > 0 && xLogPerfData.getFirstValue().p.endTime < time_start) {
			xLogPerfData.removeFirst();
		}
		if (this.endTime > 0) {
			while (xLogPerfData.size() > 0 && xLogPerfData.getLastValue().p.endTime > time_end) {
				xLogPerfData.removeLast();
			}
		}
	}
	
	ObjectSelectManager objSelMgr = ObjectSelectManager.getInstance();
	int count;
	
	private void drawXPerfData(GC gc, long time_start, long time_end, int chart_x,
			int chart_y, int chart_w, int chart_h) {
		count = 0;
		if (xLogPerfData.size() == 0)
			return;
		removeOutsidePerfData(time_start, time_end);
		Enumeration<XLogData> en = xLogPerfData.values();
		while (en.hasMoreElements()) {
			XLogData d = en.nextElement();
			if (d == null || d.p.endTime < time_start || d.p.endTime > time_end) {
				continue;
			}
			if (d.filter_hash != filter_hash) {
				if (isFilterOk(d)) {
					d.filter_ok = true;
				} else {
					d.filter_ok = false;
				}
				d.filter_hash = filter_hash;
			}
			if (d.filter_ok) {
				if (objSelMgr.isUnselectedObject(d.p.objHash)) {
					d.x = d.y = -1;
					continue;
				}
				int x = (int) (chart_w * (d.p.endTime - time_start) / xTimeRange);
				d.x = chart_x + x;
				
				int y;
				if ((double) d.p.elapsed / 1000 >= yValueMax) {
					y = chart_h - 1;
				} else {
					y = (int) (chart_h * ((double) d.p.elapsed / 1000 - yValueMin) / (yValueMax - yValueMin));
				}

				d.y = chart_h + chart_y - y;
				
				if (pointMap.check(x, y)) {
					try {
						if (d.p.error != 0) { 
//							gc.setForeground(ColorUtil.getInstance().getColor("red"));
//							gc.drawString(MARK, d.x, d.y, true);
							gc.drawImage(dotImage.getXPErrorImage(), d.x, d.y);
						} else {
//							gc.setForeground(AgentColorManager.getInstance().getColor(d.p.objHash));
//							gc.drawString(MARK, d.x, d.y, true);
							gc.drawImage(dotImage.getXPImage(d.p.objHash), d.x, d.y);
						}
					} catch (Throwable t) {
					}
				}
				count++;
			}
		}
	}
	
	public double getYValue() {
		return yValueMax;
	}

	public void setYValueMaxValue(double max) {
		yValueMax = max;
		build();
		mouse.setNewYValue();
	}

	public int moveWidth = 0;
	
	public void keyPressed(int keyCode) {
		switch (keyCode) {
		case 16777217:// UP Key
			yValueMax += yValueMax >= 2 ? 1 : 0.1;
			break;
		case 16777218: // DOWN Key
			yValueMax -= yValueMax >= 2 ? 1 : (yValueMax < 0.5 ? 0.05 : 0.1);
			if (yValueMax < 0.05)
				yValueMax = 0.05;
			break;
		case 16777261:
		case 45: // -
			xTimeRange += DateUtil.MILLIS_PER_MINUTE;
			load();
			break;
		case 16777259:
		case 61: // +
			xTimeRange -= DateUtil.MILLIS_PER_MINUTE;
			if (xTimeRange < DateUtil.MILLIS_PER_MINUTE)
				xTimeRange = DateUtil.MILLIS_PER_MINUTE;
			break;
		case 16777219: // left arrow
			moveWidth -= DateUtil.MILLIS_PER_SECOND * 10;
			load();
			break;
		case 16777220: // right arrow
			moveWidth +=  DateUtil.MILLIS_PER_SECOND * 10;
			load();
			break;
		}
	}
	
	private boolean zoomMode = false;
	private long zoomEndtime;
	
	public boolean zoomIn(long stime, long etime) {
		if (this.endTime < etime 
				|| this.endTime - xTimeRange > stime
				|| etime < stime) {
			MessageDialog.openWarning(Display.getCurrent().getActiveShell(), "Warning", "MaxRange is between " + DateUtil.format(this.endTime - xTimeRange, "HH:mm") + " and " +  DateUtil.format(this.endTime, "HH:mm"));
			return false;
		}
		zoomEndtime = etime;
		xTimeRange = etime - stime;
		moveWidth = 0;
		zoomMode = true;
		build();
		return true;
	}
	
	public void endZoom() {
		initializeChart();
	}
	
	public boolean isZoomMode() {
		return zoomMode;
	}
	
	public void setServerId(int serverId) {
		this.serverId = serverId;
	}

	private void load() {
		if (zoomMode) {
			return;
		}
		long time_end = (this.endTime > 0 ? this.endTime : TimeUtil.getCurrentTime()) + moveWidth ;
		long time_start = time_end - xTimeRange;
		
		if (time_end > TimeUtil.getCurrentTime()) {
			moveWidth -= DateUtil.MILLIS_PER_SECOND * 10;
			return;
		}
		callback.timeRangeChanged(time_start, time_end);
	}
	
	public boolean isFilterOk(XLogData d) {
		return isObjNameFilterOk(d)
				&& isServiceFilterOk(d)
				&& isIpFilterOk(d.p)
				&& isSelectedUrlFilterOk(d.p.service)
				&& isErrorFilterOk(d.p)
				&& isApicallFilterOk(d.p)
				&& isSqlFilterOk(d.p);
	}
	
	public boolean isObjNameFilterOk(XLogData d) {
		if (StringUtil.isEmpty(this.objName)) {
			return true;
		}
		String objName = TextProxy.object.getLoadText(yyyymmdd, d.p.objHash, d.serverId);
		return objNameMat.include(objName);
	}
	
	public boolean isServiceFilterOk(XLogData d) {
		if (StringUtil.isEmpty(this.service)) {
			return true;
		}
		String serviceName = TextProxy.service.getLoadText(yyyymmdd, d.p.service, d.serverId);
		return serviceMat.include(serviceName);
	}
	
	public boolean isIpFilterOk(XLogPack p) {
		if (StringUtil.isEmpty(this.ip)) {
			return true;
		}
		String value = IPUtil.toString(p.ipaddr);
		return ipMat.include(value);
	}
	
	public boolean isSelectedUrlFilterOk(int urlHash) {
		boolean result = false;
		if (selectedUrlHash == 0 || selectedUrlHash == urlHash) {
			result = true;
		}
		return result;
	}
	
	public boolean isErrorFilterOk(XLogPack p) {
		if (onlyError) {
			return p.error != 0;
		}
		return true;
	}
	
	public boolean isSqlFilterOk(XLogPack p) {
		if (onlySql) {
			return p.sqlCount > 0; 
		}
		return true;
	}
	
	public boolean isApicallFilterOk(XLogPack p) {
		if (onlyApicall) {
			return p.apicallCount > 0; 
		}
		return true;
	}
	
	public void createFilterHash() {
		filter_hash = HashUtil.hash(objName);
		filter_hash ^= HashUtil.hash(service);
		filter_hash ^= HashUtil.hash(ip);
		filter_hash ^= HashUtil.hash(onlyError ? "onlyError" : "");
		filter_hash ^= HashUtil.hash(onlySql ? "onlySql" : "");
		filter_hash ^=  HashUtil.hash(onlyApicall ? "onlyApicall" : "");
		filter_hash ^= selectedUrlHash ^ (selectedUrlHash >> 16);
	}
	
	public interface ITimeChange {
		public void timeRangeChanged(long stime, long etime);
	}
}
