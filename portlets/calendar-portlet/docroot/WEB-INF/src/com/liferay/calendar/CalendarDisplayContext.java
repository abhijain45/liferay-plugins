/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
 
package com.liferay.calendar;
 
import com.liferay.calendar.model.Calendar;
import com.liferay.calendar.model.CalendarResource;
import com.liferay.calendar.service.CalendarLocalServiceUtil;
import com.liferay.calendar.service.CalendarServiceUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.model.Group;
import com.liferay.portal.service.GroupLocalServiceUtil;
import com.liferay.portal.theme.ThemeDisplay;
 
import java.util.ArrayList;
import java.util.List;
 
/**
 * @author Adam Brandizzi
 */
public class CalendarDisplayContext {
 
	public CalendarDisplayContext(ThemeDisplay themeDisplay) {

		_themeDisplay = themeDisplay;
	}
 
	public List<Calendar> getOtherCalendars(long[] calendarIds)
		throws PortalException, SystemException {

		List<Calendar> otherCalendars = new ArrayList<Calendar>();

		for (long calendarId : calendarIds) {
			Calendar calendar = CalendarServiceUtil.fetchCalendar(calendarId);

			if (calendar == null) {
				continue;
			}
			CalendarResource calendarResource =	calendar.getCalendarResource();

			if (!calendarResource.isActive()) {
				continue;
			}

			Group scopeGroup = _themeDisplay.getScopeGroup();

			long scopeGroupId = scopeGroup.getGroupId();
			long scopeLiveGroupId = scopeGroup.getLiveGroupId();

			Group calendarGroup = GroupLocalServiceUtil.getGroup(
				calendar.getGroupId());

			long calendarGroupId = calendarGroup.getGroupId();

			if (scopeGroup.isStagingGroup()) {
				if (calendarGroup.isStagingGroup()) {
					if (scopeGroupId != calendarGroupId) {
						calendar = CalendarLocalServiceUtil.
							fetchCalendarByUuidAndGroupId(
								calendar.getUuid(),	
									calendarGroup.getLiveGroupId());
					}
				}
				else if (scopeLiveGroupId == calendarGroupId) {
					Group stagingGroup = calendarGroup.getStagingGroup();
					calendar = CalendarLocalServiceUtil.
						fetchCalendarByUuidAndGroupId(
							calendar.getUuid(),
								stagingGroup.getLiveGroupId());
					
				}
			}
				
			else if (calendarGroup.isStagingGroup()) {
				calendar = CalendarLocalServiceUtil.
					fetchCalendarByUuidAndGroupId(
						calendar.getUuid(), calendarGroup.getLiveGroupId());
					
			}
			if (calendar == null) {
				continue;
			}

			otherCalendars.add(calendar);
		}

		return otherCalendars;
	}

	private final ThemeDisplay _themeDisplay;
 
 } 